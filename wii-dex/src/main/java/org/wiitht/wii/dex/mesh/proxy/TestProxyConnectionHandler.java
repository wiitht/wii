package org.wiitht.wii.dex.mesh.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http2.*;
import org.wiitht.wii.dex.proxy.NettyProxyHandler;

import org.wiitht.wii.dex.proxy.SendDataCommand;
import java.util.logging.Level;

public class TestProxyConnectionHandler  extends Http2ConnectionHandler {

    protected TestProxyConnectionHandler(Http2ConnectionDecoder decoder, Http2ConnectionEncoder encoder, Http2Settings initialSettings) {
        super(decoder, encoder, initialSettings);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof SendDataCommand) {
            SendDataCommand command = (SendDataCommand)msg;
            ctx.write(command.content());
        } else {
            ctx.write(msg, promise);
        }
    }


}
