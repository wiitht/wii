package org.wiitht.wii.core.internal.factory;

import org.wiitht.wii.core.common.config.ServerProperties;
import org.wiitht.wii.core.proxy.ProxyH2ConnectionHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @Author wii
 * @Date 18-12-24-下午6:18
 * @Version 1.0
 */
public class ServerBuilder {
    private ConfigBuilder configBuilder;

    public static ServerBuilder newBuilder(){
        return new ServerBuilder();
    }

    public ServerBuilder setConfigBuilder(ConfigBuilder configBuilder){
        this.configBuilder = configBuilder;
        return this;
    }

    public void start(ServerProperties serverProperties){
        try {
            EventLoopGroup bossGroup = new NioEventLoopGroup();
            EventLoopGroup workerGroup = new NioEventLoopGroup(2);
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel channel) throws Exception {
                            ProxyH2ConnectionHandler handler = ProxyH2ConnectionHandler.newHandler(configBuilder.builder());
                            channel.pipeline().addLast("proxyHandler", handler);
                        }
                    });
            ChannelFuture future = bootstrap.bind(serverProperties.getPort()).sync();
            future.channel().closeFuture().sync();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

}