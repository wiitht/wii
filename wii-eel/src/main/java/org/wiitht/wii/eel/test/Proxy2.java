package org.wiitht.wii.eel.test;

import org.wiitht.wii.eel.proxy.NettyProxyHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @Author tanghong
 * @Date 18-8-15-下午5:18
 * @Version 1.0
 */
public class Proxy2 {
    public static void main(String[] args){
        try {
            EventLoopGroup bossGroup = new NioEventLoopGroup();
            EventLoopGroup workerGroup = new NioEventLoopGroup(2);
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup).
                    channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            NettyProxyHandler handler = HelloHandler.createHandler(ch.newPromise());
                            ch.pipeline().addLast("handler", handler);
                        }
                    });

            ChannelFuture f = b
                    .bind(8083)
                    .sync();
            f.channel().closeFuture().sync();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
