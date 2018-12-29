package org.wiitht.wii.core.internal.client;

import org.wiitht.wii.core.internal.manage.Deliverer;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.net.InetSocketAddress;
/**
 * @Author wii
 * @Date 18-12-26-下午8:50
 * @Version 1.0
 */
public class ClientBuilder {
    private Deliverer deliverer;

    public static ClientBuilder newBuilder() {
        return new ClientBuilder();
    }

    public ClientBuilder deliver(Deliverer deliverer) {
        this.deliverer = deliverer;
        return this;
    }

    public void start(String address, int port) {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline pipeline = ch.pipeline();
                            ClientH2ConnectionHandler handler = ClientH2ConnectionHandler.newHandler(deliverer);
                            pipeline.addLast("clientHandler", handler);
                        }
                    });
            bootstrap.register();
            ChannelFuture future = bootstrap.connect(new InetSocketAddress(address, port)).sync();
            Channel channel = future.channel();
            deliverer.initChannel(channel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}