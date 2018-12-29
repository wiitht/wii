package org.wiitht.wii.core.internal.manage;

/**
 * 接收deliverer返回的各类消息，并且协调相应的transmitter处理；
 */
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * @Author tanghong
 * @Date 18-12-24-下午3:41
 * @Version 1.0
 */
public class Receiver {
    private ChannelHandlerContext proxyCtx;

    public Receiver(ChannelHandlerContext ctx){
        proxyCtx = ctx;
    }

    public void response(ByteBuf msg){
        proxyCtx.writeAndFlush(msg.copy());
    }

}
