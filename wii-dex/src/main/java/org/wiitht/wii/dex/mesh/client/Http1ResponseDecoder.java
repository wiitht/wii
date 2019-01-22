package org.wiitht.wii.dex.mesh.client;

import com.linecorp.armeria.common.*;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.logging.RequestLogBuilder;
import com.linecorp.armeria.internal.ArmeriaHttpUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:29
 * @Version 1.0
 */
public class Http1ResponseDecoder extends HttpResponseDecoder implements ChannelInboundHandler {

    private static final Logger logger = LoggerFactory.getLogger(Http1ResponseDecoder.class);

    private enum State {
        NEED_HEADERS,
        NEED_INFORMATIONAL_DATA,
        NEED_DATA_OR_TRAILING_HEADERS,
        DISCARD
    }

    /** The request being decoded currently. */
    @Nullable
    private HttpResponseWrapper res;
    private int resId = 1;
    private Http1ResponseDecoder.State state = Http1ResponseDecoder.State.NEED_HEADERS;

    Http1ResponseDecoder(Channel channel) {
        super(channel);
    }

    @Override
    HttpResponseWrapper addResponse(
            int id, @Nullable HttpRequest req, DecodedHttpResponse res, RequestLogBuilder logBuilder,
            long responseTimeoutMillis, long maxContentLength) {

        final HttpResponseWrapper resWrapper =
                super.addResponse(id, req, res, logBuilder, responseTimeoutMillis, maxContentLength);

        resWrapper.completionFuture().whenComplete((unused, cause) -> {
            // Cancel timeout future and abort the request if it exists.
            resWrapper.onSubscriptionCancelled();

            if (cause != null) {
                // Disconnect when the response has been closed with an exception because there's no way
                // to recover from it in HTTP/1.
                channel().close();
            }
        });

        return resWrapper;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {}

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {}

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelRegistered();
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelUnregistered();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (res != null) {
            res.close(ClosedSessionException.get());
        }
        ctx.fireChannelInactive();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof HttpObject)) {
            ctx.fireChannelRead(msg);
            return;
        }

        try {
            switch (state) {
                case NEED_HEADERS:
                    if (msg instanceof HttpResponse) {
                        final HttpResponse nettyRes = (HttpResponse) msg;
                        final DecoderResult decoderResult = nettyRes.decoderResult();
                        if (!decoderResult.isSuccess()) {
                            fail(ctx, new ProtocolViolationException(decoderResult.cause()));
                            return;
                        }

                        if (!HttpUtil.isKeepAlive(nettyRes)) {
                            disconnectWhenFinished();
                        }

                        final HttpResponseWrapper res = getResponse(resId);
                        assert res != null;
                        this.res = res;

                        if (nettyRes.status().codeClass() == HttpStatusClass.INFORMATIONAL) {
                            state = Http1ResponseDecoder.State.NEED_INFORMATIONAL_DATA;
                        } else {
                            state = Http1ResponseDecoder.State.NEED_DATA_OR_TRAILING_HEADERS;
                        }

                        res.scheduleTimeout(channel().eventLoop());
                        res.write(ArmeriaHttpUtil.toArmeria(nettyRes));
                    } else {
                        failWithUnexpectedMessageType(ctx, msg);
                    }
                    break;
                case NEED_INFORMATIONAL_DATA:
                    if (msg instanceof LastHttpContent) {
                        state = Http1ResponseDecoder.State.NEED_HEADERS;
                    } else {
                        failWithUnexpectedMessageType(ctx, msg);
                    }
                    break;
                case NEED_DATA_OR_TRAILING_HEADERS:
                    if (msg instanceof HttpContent) {
                        final HttpContent content = (HttpContent) msg;
                        final DecoderResult decoderResult = content.decoderResult();
                        if (!decoderResult.isSuccess()) {
                            fail(ctx, new ProtocolViolationException(decoderResult.cause()));
                            return;
                        }

                        final ByteBuf data = content.content();
                        final int dataLength = data.readableBytes();
                        if (dataLength > 0) {
                            assert res != null;
                            final long maxContentLength = res.maxContentLength();
                            if (maxContentLength > 0 && res.writtenBytes() > maxContentLength - dataLength) {
                                fail(ctx, ContentTooLargeException.get());
                                return;
                            } else {
                                res.write(HttpData.of(data));
                            }
                        }

                        if (msg instanceof LastHttpContent) {
                            final HttpResponseWrapper res = removeResponse(resId++);
                            assert res != null;
                            assert this.res == res;
                            this.res = null;

                            state = Http1ResponseDecoder.State.NEED_HEADERS;

                            final HttpHeaders trailingHeaders = ((LastHttpContent) msg).trailingHeaders();
                            if (!trailingHeaders.isEmpty()) {
                                res.write(ArmeriaHttpUtil.toArmeria(trailingHeaders));
                            }

                            res.close();

                            if (needsToDisconnect()) {
                                ctx.close();
                            }
                        }
                    } else {
                        failWithUnexpectedMessageType(ctx, msg);
                    }
                    break;
                case DISCARD:
                    break;
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private void failWithUnexpectedMessageType(ChannelHandlerContext ctx, Object msg) {
        fail(ctx, new ProtocolViolationException(
                "unexpected message type: " + msg.getClass().getName()));
    }

    private void fail(ChannelHandlerContext ctx, Throwable cause) {
        state = Http1ResponseDecoder.State.DISCARD;

        final HttpResponseWrapper res = this.res;
        this.res = null;

        if (res != null) {
            res.close(cause);
        } else {
            logger.warn("Unexpected exception:", cause);
        }

        ctx.close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        ctx.fireUserEventTriggered(evt);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}
