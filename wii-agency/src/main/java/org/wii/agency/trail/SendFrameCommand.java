package org.wii.agency.trail;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;

/**
 * @Author tanghong
 * @Date 18-8-21-下午6:59
 * @Version 1.0
 */
public class SendFrameCommand  extends DefaultByteBufHolder implements ProxyWriteQueue.QueuedCommand {
    private final StreamIdHolder stream;
    private final boolean endStream;

    private ChannelPromise promise;

    public SendFrameCommand(StreamIdHolder stream, ByteBuf content, boolean endStream) {
        super(content.copy());
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
        return new SendFrameCommand(stream, content().copy(), endStream);
    }

    @Override
    public ByteBufHolder duplicate() {
        return new SendFrameCommand(stream, content().duplicate(), endStream);
    }

    @Override
    public SendFrameCommand retain() {
        super.retain();
        return this;
    }

    @Override
    public SendFrameCommand retain(int increment) {
        super.retain(increment);
        return this;
    }

    @Override
    public SendFrameCommand touch() {
        super.touch();
        return this;
    }

    @Override
    public SendFrameCommand touch(Object hint) {
        super.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object that) {
        if (that == null || !that.getClass().equals(SendFrameCommand.class)) {
            return false;
        }
        SendFrameCommand thatCmd = (SendFrameCommand) that;
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
        channel.write(this, channel.newPromise());
    }
}
