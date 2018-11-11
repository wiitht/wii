package org.wiitht.wii.dex.mesh.client;

import com.linecorp.armeria.common.ClosedSessionException;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.util.ReferenceCountUtil;

/**
 * @Author tanghong
 * @Date 18-10-24-下午6:28
 * @Version 1.0
 */
public abstract class HttpObjectEncoder {
    private volatile boolean closed;

    protected abstract Channel channel();

    protected abstract void write(Object msg);

    protected EventLoop eventLoop() {
        return channel().eventLoop();
    }

    /**
     * Writes an {@link HttpHeaders}.
     */
    public final ChannelFuture writeHeaders(int id, int streamId, HttpHeaders headers, boolean endStream) {

        assert eventLoop().inEventLoop();

        if (closed) {
            return newClosedSessionFuture();
        }

        return doWriteHeaders(id, streamId, headers, endStream);
    }

    protected abstract ChannelFuture doWriteHeaders(
            int id, int streamId, HttpHeaders headers, boolean endStream);

    /**
     * Writes an {@link HttpData}.
     */
    public final ChannelFuture writeData(int id, int streamId, HttpData data, boolean endStream) {

        assert eventLoop().inEventLoop();

        if (closed) {
            ReferenceCountUtil.safeRelease(data);
            return newClosedSessionFuture();
        }

        return doWriteData(id, streamId, data, endStream);
    }

    protected abstract ChannelFuture doWriteData(int id, int streamId, HttpData data, boolean endStream);

    /**
     * Resets the specified stream. If the session protocol does not support multiplexing or the connection
     * is in unrecoverable state, the connection will be closed. For example, in an HTTP/1 connection, this
     * will lead the connection to be closed immediately or after the previous requests that are not reset.
     */
    public final ChannelFuture writeReset(int id, int streamId, Http2Error error) {

        if (closed) {
            return newClosedSessionFuture();
        }

        return doWriteReset(id, streamId, error);
    }

    protected abstract ChannelFuture doWriteReset(int id, int streamId, Http2Error error);

    /**
     * Releases the resources related with this encoder and fails any unfinished writes.
     */
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        doClose();
    }

    protected abstract void doClose();

    protected final ChannelFuture newClosedSessionFuture() {
        return newFailedFuture(ClosedSessionException.get());
    }

    protected final ChannelFuture newFailedFuture(Throwable cause) {
        return channel().newFailedFuture(cause);
    }

    protected final ByteBuf toByteBuf(HttpData data) {
        if (data instanceof ByteBufHolder) {
            return ((ByteBufHolder) data).content();
        }
        final ByteBuf buf = channel().alloc().directBuffer(data.length(), data.length());
        buf.writeBytes(data.array(), data.offset(), data.length());
        return buf;
    }
}
