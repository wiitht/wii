package org.wiitht.wii.dex.mesh.client;

import com.linecorp.armeria.common.*;
import com.linecorp.armeria.common.logging.RequestLogBuilder;
import com.linecorp.armeria.internal.ArmeriaHttpUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static io.netty.handler.codec.http2.Http2Error.INTERNAL_ERROR;
import static io.netty.handler.codec.http2.Http2Error.PROTOCOL_ERROR;
import static io.netty.handler.codec.http2.Http2Exception.connectionError;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:12
 * @Version 1.0
 */
public class Http2ResponseDecoder extends HttpResponseDecoder implements Http2Connection.Listener,
        Http2FrameListener {

    private static final Logger logger = LoggerFactory.getLogger(Http2ResponseDecoder.class);

    private final Http2Connection conn;
    private final Http2ConnectionEncoder encoder;

    Http2ResponseDecoder(Http2Connection conn, Channel channel, Http2ConnectionEncoder encoder) {
        super(channel);
        this.conn = conn;
        this.encoder = encoder;
    }

    @Override
    HttpResponseWrapper addResponse(
            int id, @Nullable HttpRequest req, DecodedHttpResponse res, RequestLogBuilder logBuilder,
            long responseTimeoutMillis, long maxContentLength) {

        final HttpResponseWrapper resWrapper =
                super.addResponse(id, req, res, logBuilder, responseTimeoutMillis, maxContentLength);

        resWrapper.completionFuture().whenCompleteAsync((unused, cause) -> {
            // Cancel timeout future and abort the request if it exists.
            resWrapper.onSubscriptionCancelled();

            if (cause != null) {
                // We are not closing the connection but just send a RST_STREAM,
                // so we have to remove the response manually.
                removeResponse(id);

                // Reset the stream.
                final int streamId = idToStreamId(id);
                if (conn.streamMayHaveExisted(streamId)) {
                    final ChannelHandlerContext ctx = channel().pipeline().lastContext();
                    if (ctx != null) {
                        encoder.writeRstStream(ctx, streamId, Http2Error.CANCEL.code(), ctx.newPromise());
                        ctx.flush();
                    } else {
                        // The pipeline has been cleaned up due to disconnection.
                    }
                }
            }
        }, channel().eventLoop());
        return resWrapper;
    }

    @Override
    public void onStreamAdded(Http2Stream stream) {}

    @Override
    public void onStreamActive(Http2Stream stream) {}

    @Override
    public void onStreamHalfClosed(Http2Stream stream) {}

    @Override
    public void onStreamClosed(Http2Stream stream) {
        final HttpResponseWrapper res = getResponse(streamIdToId(stream.id()), true);
        if (res != null) {
            res.close(ClosedSessionException.get());
        }
    }

    @Override
    public void onStreamRemoved(Http2Stream stream) {}

    @Override
    public void onGoAwaySent(int lastStreamId, long errorCode, ByteBuf debugData) {}

    @Override
    public void onGoAwayReceived(int lastStreamId, long errorCode, ByteBuf debugData) {}

    @Override
    public void onSettingsRead(ChannelHandlerContext ctx, Http2Settings settings) {
        ctx.fireChannelRead(settings);
    }

    @Override
    public void onSettingsAckRead(ChannelHandlerContext ctx) {}

    @Override
    public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int padding,
                              boolean endOfStream) throws Http2Exception {
        final HttpResponseWrapper res = getResponse(streamIdToId(streamId), endOfStream);
        if (res == null) {
            if (conn.streamMayHaveExisted(streamId)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} Received a late HEADERS frame for a closed stream: {}",
                            ctx.channel(), streamId);
                }
                return;
            }

            throw connectionError(PROTOCOL_ERROR, "received a HEADERS frame for an unknown stream: %d",
                    streamId);
        }

        final HttpHeaders converted = ArmeriaHttpUtil.toArmeria(headers, endOfStream);
        try {
            // If this tryWrite() returns false, it means the response stream has been closed due to
            // disconnection or by the response consumer. We do not need to handler such cases here because
            // it will be notified to the response consumer anyway.
            if (!res.tryWrite(converted)) {
                // Schedule only when the response stream is still open.
                res.scheduleTimeout(ctx.channel().eventLoop());
            }
        } catch (Throwable t) {
            res.close(t);
            throw connectionError(INTERNAL_ERROR, t, "failed to consume a HEADERS frame");
        }

        if (endOfStream) {
            res.close();
        }
    }

    @Override
    public void onHeadersRead(
            ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency,
            short weight, boolean exclusive, int padding, boolean endOfStream) throws Http2Exception {

        onHeadersRead(ctx, streamId, headers, padding, endOfStream);
    }

    @Override
    public int onDataRead(
            ChannelHandlerContext ctx, int streamId, ByteBuf data,
            int padding, boolean endOfStream) throws Http2Exception {

        final int dataLength = data.readableBytes();
        final HttpResponseWrapper res = getResponse(streamIdToId(streamId), endOfStream);
        if (res == null) {
            if (conn.streamMayHaveExisted(streamId)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} Received a late DATA frame for a closed stream: {}",
                            ctx.channel(), streamId);
                }
                return dataLength + padding;
            }

            throw connectionError(PROTOCOL_ERROR, "received a DATA frame for an unknown stream: %d",
                    streamId);
        }

        final long maxContentLength = res.maxContentLength();
        if (maxContentLength > 0 && res.writtenBytes() > maxContentLength - dataLength) {
            res.close(ContentTooLargeException.get());
            throw connectionError(INTERNAL_ERROR,
                    "content length too large: %d + %d > %d (stream: %d)",
                    res.writtenBytes(), dataLength, maxContentLength, streamId);
        }

        try {
            // If this tryWrite() returns false, it means the response stream has been closed due to
            // disconnection or by the response consumer. We do not need to handler such cases here because
            // it will be notified to the response consumer anyway.
            res.tryWrite(HttpData.of(data));
        } catch (Throwable t) {
            res.close(t);
            throw connectionError(INTERNAL_ERROR, t, "failed to consume a DATA frame");
        }

        if (endOfStream) {
            res.close();
        }

        // All bytes have been processed.
        return dataLength + padding;
    }

    @Override
    public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
        final HttpResponseWrapper res = removeResponse(streamIdToId(streamId));
        if (res == null) {
            if (conn.streamMayHaveExisted(streamId)) {
                if (logger.isDebugEnabled()) {
                    logger.debug("{} Received a late RST_STREAM frame for a closed stream: {}",
                            ctx.channel(), streamId);
                }
            } else {
                throw connectionError(PROTOCOL_ERROR,
                        "received a RST_STREAM frame for an unknown stream: %d", streamId);
            }
            return;
        }

        res.close(ClosedSessionException.get());
    }

    @Override
    public void onPushPromiseRead(ChannelHandlerContext ctx, int streamId, int promisedStreamId,
                                  Http2Headers headers, int padding) {}

    @Override
    public void onPriorityRead(ChannelHandlerContext ctx, int streamId, int streamDependency, short weight,
                               boolean exclusive) {}

    @Override
    public void onPingRead(ChannelHandlerContext ctx, long data) {}

    @Override
    public void onPingAckRead(ChannelHandlerContext ctx, long data) {}

    @Override
    public void onGoAwayRead(ChannelHandlerContext ctx, int lastStreamId, long errorCode, ByteBuf debugData) {}

    @Override
    public void onWindowUpdateRead(ChannelHandlerContext ctx, int streamId, int windowSizeIncrement) {}

    @Override
    public void onUnknownFrame(ChannelHandlerContext ctx, byte frameType, int streamId, Http2Flags flags,
                               ByteBuf payload) {}

    private static int streamIdToId(int streamId) {
        return streamId - 1 >>> 1;
    }

    private static int idToStreamId(int id) {
        return (id << 1) + 1;
    }
}
