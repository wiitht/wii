package org.wiitht.wii.core.internal.manage;

/**
 * 1）负责将转发的请求数据传递给client，并其管理与控制client；
 */
import org.wiitht.wii.core.internal.client.ClientBuilder;
import org.wiitht.wii.core.internal.client.DeliverCommand;
import org.wiitht.wii.core.internal.message.MessageQueue;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http2.Http2Headers;

/**
 * @Author wii
 * @Date 18-12-24-下午3:40
 * @Version 1.0
 */
public class Deliverer {
    private Receiver receiver;
    private Channel channel;
    private MessageQueue messageQueue;

    public Receiver getReceiver() {
        return receiver;
    }

    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    public Channel getChannel() {
        return channel;
    }

    //路由header的时候初始化
    public void initChannel(Channel channel) {
        this.channel = channel;
        this.messageQueue = new MessageQueue(channel);
    }

    public void sendHeaderCommand(DeliverCommand.DeliverHeaderCommand command){
        messageQueue.enqueue(command, true);
    }

    public void sendHeaderCommand(int streamId, Http2Headers headers){
        DeliverCommand deliverCommand = new DeliverCommand();
        DeliverCommand.DeliverHeaderCommand command = deliverCommand.new DeliverHeaderCommand(streamId, headers);
        messageQueue.enqueue(command, true);
    }

    public void sendDataCommand(DeliverCommand.DeliverDataCommand command){
        messageQueue.enqueue(command, true);
    }

    public void sendDataCommand(int streamId, ByteBuf data, int padding, boolean endOfStream){
        DeliverCommand deliverCommand = new DeliverCommand();
        DeliverCommand.DeliverDataCommand command = deliverCommand.new DeliverDataCommand(streamId, data, endOfStream);
        messageQueue.enqueue(command, true);
    }

    public void start(String address, int port){
        ClientBuilder clientBuilder = ClientBuilder.newBuilder();
        clientBuilder.deliver(this);
        clientBuilder.start(address, port);
    }
}