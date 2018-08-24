package org.wii.agency.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.DefaultByteBufHolder;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
/**
 * @Author tanghong
 * @Date 18-8-24-下午1:23
 * @Version 1.0
 */
public class SendDataCommand extends DefaultByteBufHolder implements WriteQueue.QueuedCommand {

    private ChannelPromise promise;

    public SendDataCommand(ByteBuf data) {
        super(data);
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
