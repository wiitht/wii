package org.wiitht.wii.dex.mesh.client;

import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.SessionProtocolNegotiationException;
import com.linecorp.armeria.common.ClosedSessionException;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.util.Exceptions;
import com.linecorp.armeria.internal.InboundTrafficController;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.codec.http2.Http2ConnectionHandler;
import io.netty.handler.codec.http2.Http2ConnectionPrefaceAndSettingsFrameWrittenEvent;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.SslCloseCompletionEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.concurrent.ScheduledFuture;

import static com.linecorp.armeria.common.SessionProtocol.*;
import static com.linecorp.armeria.common.SessionProtocol.H1C;
import static java.util.Objects.requireNonNull;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:29
 * @Version 1.0
 */
public class HttpSessionHandler extends ChannelDuplexHandler implements HttpSession {

    private static final Logger logger = LoggerFactory.getLogger(HttpSessionHandler.class);

    /**
     * 2^29 - We could have used 2^30 but this should be large enough.
     */
    private static final int MAX_NUM_REQUESTS_SENT = 536870912;

    private final HttpSessionChannelFactory channelFactory;
    private final Channel channel;
    private final Promise<Channel> sessionPromise;
    private final ScheduledFuture<?> sessionTimeoutFuture;

    /**
     * Whether the current channel is active or not.
     */
    private volatile boolean active;

    /**
     * The current negotiated {@link SessionProtocol}.
     */
    @Nullable
    private SessionProtocol protocol;

    @Nullable
    private HttpResponseDecoder responseDecoder;
    @Nullable
    private HttpObjectEncoder requestEncoder;

    /**
     * The number of requests sent. Disconnects when it reaches at {@link #MAX_NUM_REQUESTS_SENT}.
     */
    private int numRequestsSent;

    /**
     * {@code true} if the protocol upgrade to HTTP/2 has failed.
     * If set to {@code true}, another connection attempt will follow.
     */
    private boolean needsRetryWithH1C;

    HttpSessionHandler(HttpSessionChannelFactory channelFactory, Channel channel,
                       Promise<Channel> sessionPromise, ScheduledFuture<?> sessionTimeoutFuture) {

        this.channelFactory = requireNonNull(channelFactory, "channelFactory");
        this.channel = requireNonNull(channel, "channel");
        this.sessionPromise = requireNonNull(sessionPromise, "sessionPromise");
        this.sessionTimeoutFuture = requireNonNull(sessionTimeoutFuture, "sessionTimeoutFuture");
    }

    @Override
    public SessionProtocol protocol() {
        return protocol;
    }

    @Override
    public InboundTrafficController inboundTrafficController() {
        assert responseDecoder != null;
        return responseDecoder.inboundTrafficController();
    }

    @Override
    public boolean hasUnfinishedResponses() {
        assert responseDecoder != null;
        return responseDecoder.hasUnfinishedResponses();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean invoke(ClientRequestContext ctx, HttpRequest req, DecodedHttpResponse res) {
        if (handleEarlyCancellation(ctx, req, res)) {
            return true;
        }

        final long writeTimeoutMillis = ctx.writeTimeoutMillis();
        final long responseTimeoutMillis = ctx.responseTimeoutMillis();
        final long maxContentLength = ctx.maxResponseLength();

        assert responseDecoder != null;
        assert requestEncoder != null;

        final int numRequestsSent = ++this.numRequestsSent;
        final HttpResponseDecoder.HttpResponseWrapper wrappedRes =
                responseDecoder.addResponse(numRequestsSent, req, res, ctx.logBuilder(),
                        responseTimeoutMillis, maxContentLength);
        req.subscribe(
                new HttpRequestSubscriber(channel, requestEncoder,
                        numRequestsSent, req, wrappedRes, ctx,
                        writeTimeoutMillis),
                channel.eventLoop(),
                true);

        if (numRequestsSent >= MAX_NUM_REQUESTS_SENT) {
            responseDecoder.disconnectWhenFinished();
            return false;
        } else {
            return true;
        }
    }

    private boolean handleEarlyCancellation(ClientRequestContext ctx, HttpRequest req,
                                            DecodedHttpResponse res) {
        if (res.isOpen()) {
            return false;
        }

        // The response has been closed even before its request is sent.
        assert protocol != null;

        req.abort();
        ctx.logBuilder().startRequest(channel, protocol);
        ctx.logBuilder().requestHeaders(req.headers());
        req.completionFuture().whenComplete((unused, cause) -> {
            if (cause == null) {
                ctx.logBuilder().endRequest();
            } else {
                ctx.logBuilder().endRequest(cause);
            }
        });
        res.completionFuture().whenComplete((unused, cause) -> {
            if (cause == null) {
                ctx.logBuilder().endResponse();
            } else {
                ctx.logBuilder().endResponse(cause);
            }
        });

        return true;
    }

    @Override
    public void retryWithH1C() {
        needsRetryWithH1C = true;
    }

    @Override
    public void deactivate() {
        active = false;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        active = channel.isActive();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        active = true;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Http2Settings) {
            // Expected
        } else {
            try {
                final String typeInfo;
                if (msg instanceof ByteBuf) {
                    typeInfo = msg + " HexDump: " + ByteBufUtil.hexDump((ByteBuf) msg);
                } else {
                    typeInfo = String.valueOf(msg);
                }
                throw new IllegalStateException("unexpected message type: " + typeInfo);
            } finally {
                ReferenceCountUtil.release(msg);
            }
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof SessionProtocol) {
            assert protocol == null;
            assert responseDecoder == null;

            sessionTimeoutFuture.cancel(false);

            // Set the current protocol and its associated WaitsHolder implementation.
            final SessionProtocol protocol = (SessionProtocol) evt;
            this.protocol = protocol;
            if (protocol == H1 || protocol == H1C) {
                requestEncoder = new Http1ObjectEncoder(channel, false, protocol.isTls());
                responseDecoder = ctx.pipeline().get(Http1ResponseDecoder.class);
            } else if (protocol == H2 || protocol == H2C) {
                final Http2ConnectionHandler handler = ctx.pipeline().get(Http2ConnectionHandler.class);
                requestEncoder = new Http2ObjectEncoder(ctx, handler.encoder());
                responseDecoder = ctx.pipeline().get(Http2ClientConnectionHandler.class).responseDecoder();
            } else {
                throw new Error(); // Should never reach here.
            }

            if (!sessionPromise.trySuccess(channel)) {
                // Session creation has been failed already; close the connection.
                ctx.close();
            }
            return;
        }

        if (evt instanceof SessionProtocolNegotiationException) {
            sessionTimeoutFuture.cancel(false);
            sessionPromise.tryFailure((SessionProtocolNegotiationException) evt);
            ctx.close();
            return;
        }

        if (evt instanceof Http2ConnectionPrefaceAndSettingsFrameWrittenEvent ||
                evt instanceof SslCloseCompletionEvent ||
                evt instanceof ChannelInputShutdownReadComplete) {
            // Expected events
            return;
        }

        logger.warn("{} Unexpected user event: {}", channel, evt);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        active = false;

        // Protocol upgrade has failed, but needs to retry.
        if (needsRetryWithH1C) {
            assert responseDecoder == null || !responseDecoder.hasUnfinishedResponses();
            sessionTimeoutFuture.cancel(false);
            channelFactory.connect(channel.remoteAddress(), H1C, sessionPromise);
        } else {
            // Fail all pending responses.
            failUnfinishedResponses(ClosedSessionException.get());

            // Cancel the timeout and reject the sessionPromise just in case the connection has been closed
            // even before the session protocol negotiation is done.
            sessionTimeoutFuture.cancel(false);
            sessionPromise.tryFailure(ClosedSessionException.get());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Exceptions.logIfUnexpected(logger, channel, protocol(), cause);
        if (channel.isActive()) {
            ctx.close();
        }
    }

    private void failUnfinishedResponses(Throwable e) {
        final HttpResponseDecoder responseDecoder = this.responseDecoder;
        if (responseDecoder == null) {
            return;
        }

        responseDecoder.failUnfinishedResponses(e);
    }
}

