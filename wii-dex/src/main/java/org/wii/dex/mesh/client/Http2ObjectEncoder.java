package org.wii.dex.mesh.client;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.stream.ClosedPublisherException;
import com.linecorp.armeria.internal.ArmeriaHttpUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.ReferenceCountUtil;

import static java.util.Objects.requireNonNull;

/**
 * @Author tanghong
 * @Date 18-10-24-下午6:25
 * @Version 1.0
 */
public class Http2ObjectEncoder extends HttpObjectEncoder {

    private final ChannelHandlerContext ctx;
    private final Http2ConnectionEncoder encoder;

    public Http2ObjectEncoder(ChannelHandlerContext ctx, Http2ConnectionEncoder encoder) {
        this.ctx = requireNonNull(ctx, "ctx");
        this.encoder = requireNonNull(encoder, "encoder");
    }

    @Override
    protected Channel channel() {
        return ctx.channel();
    }

    @Override
    protected ChannelFuture doWriteHeaders(int id, int streamId, HttpHeaders headers, boolean endStream) {
        if (!isWritable(streamId)) {
            return newFailedFuture(ClosedPublisherException.get());
        }

        return encoder.writeHeaders(
                ctx, streamId, ArmeriaHttpUtil.toNettyHttp2(headers), 0, endStream, ctx.newPromise());
    }

    @Override
    protected ChannelFuture doWriteData(int id, int streamId, HttpData data, boolean endStream) {
        if (!isWritable(streamId)) {
            ReferenceCountUtil.safeRelease(data);
            return data.isEmpty() ? ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
                    : newFailedFuture(ClosedPublisherException.get());
        }

        if (!encoder.connection().streamMayHaveExisted(streamId)) {
            // Cannot start a new stream with a DATA frame. It must start with a HEADERS frame.
            ReferenceCountUtil.safeRelease(data);
            return newFailedFuture(new IllegalStateException(
                    "cannot start a new stream " + streamId + " with a DATA frame"));
        }

        return encoder.writeData(ctx, streamId, toByteBuf(data), 0, endStream, ctx.newPromise());
    }

    @Override
    protected ChannelFuture doWriteReset(int id, int streamId, Http2Error error) {
        if (encoder.connection().streamMayHaveExisted(streamId)) {
            return encoder.writeRstStream(ctx, streamId, error.code(), ctx.newPromise());
        }

        // Tried to send a RST frame for a non-existent stream. This can happen when a client-side
        // subscriber terminated its response stream even before the first frame of the stream is sent.
        // In this case, we don't need to send a RST stream.
        return ctx.writeAndFlush(Unpooled.EMPTY_BUFFER);
    }

    @Override
    public void write(Object msg){
        ctx.write(msg);
    }

    /**
     * Returns {@code true} if the encoder can write something to the specified {@code streamId}.
     */
    private boolean isWritable(int streamId) {
        final Http2Stream stream = encoder.connection().stream(streamId);
        if (stream != null) {
            switch (stream.state()) {
                case RESERVED_LOCAL:
                case OPEN:
                case HALF_CLOSED_REMOTE:
                    return true;
                default:
                    // The response has been sent already.
                    return false;
            }
        }

        // Return false if the stream has been completely closed and removed.
        return !encoder.connection().streamMayHaveExisted(streamId);
    }

    @Override
    protected void doClose() {}
}
