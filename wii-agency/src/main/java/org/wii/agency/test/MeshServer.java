package org.wii.agency.test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.wii.agency.mesh.proxy.ProxyConnectionHandler;
/**
 * @Author tanghong
 * @Date 18-10-23-下午6:29
 * @Version 1.0
 */
public class MeshServer {
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
                            ch.pipeline().addLast("handler", new ProxyConnectionHandler());
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
