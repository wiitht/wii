package org.wii.agency.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import org.wii.agency.trail.StreamIdHolder;

/**
 * @Author tanghong
 * @Date 18-8-22-下午7:33
 * @Version 1.0
 */
public class SendProxyFrameCommand extends DefaultByteBufHolder implements WriteQueue.QueuedCommand {
    private final StreamIdHolder stream;
    private final boolean endStream;

    private ChannelPromise promise;

    public SendProxyFrameCommand(StreamIdHolder stream, ByteBuf content, boolean endStream) {
        super(content);
        this.stream = stream;
        this.endStream = endStream;
    }

    int streamId() {
        return stream.id();
    }

    boolean endStream() {
        return endStream;
    }

    @Override
    public ByteBufHolder copy() {
        return new SendProxyFrameCommand(stream, content().copy(), endStream);
    }

    @Override
    public ByteBufHolder duplicate() {
        return new SendProxyFrameCommand(stream, content().duplicate(), endStream);
    }

    @Override
    public SendProxyFrameCommand retain() {
        super.retain();
        return this;
    }

    @Override
    public SendProxyFrameCommand retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public SendProxyFrameCommand touch() {
        super.touch();
        return this;
    }

    @Override
    public SendProxyFrameCommand touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object that) {
        if (that == null || !that.getClass().equals(SendProxyFrameCommand.class)) {
            return false;
        }
        SendProxyFrameCommand thatCmd = (SendProxyFrameCommand) that;
        return thatCmd.stream.equals(stream) && thatCmd.endStream == endStream
                && thatCmd.content().equals(content());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(streamId=" + streamId()
                + ", endStream=" + endStream + ", content=" + content()
                + ")";
    }

    @Override
    public int hashCode() {
        int hash = content().hashCode();
        hash = hash * 31 + stream.hashCode();
        if (endStream) {
            hash = -hash;
        }
        return hash;
    }

    @Override
    public ChannelPromise promise() {
        return promise;
    }

    @Override
    public void promise(ChannelPromise promise) {
        this.promise = promise;
    }

    @Override
    public final void run(Channel channel) {
        channel.write(this, promise);
    }
}
