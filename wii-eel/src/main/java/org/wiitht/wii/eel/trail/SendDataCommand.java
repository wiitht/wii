package org.wiitht.wii.eel.trail;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;

/**
 * @Author tanghong
 * @Date 18-10-25-下午12:10
 * @Version 1.0
 */
public class SendDataCommand extends DefaultByteBufHolder implements ProxyWriteQueue.QueuedCommand  {
    private ChannelPromise promise;

    public SendDataCommand(ByteBuf data) {
        super(data.copy());
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
