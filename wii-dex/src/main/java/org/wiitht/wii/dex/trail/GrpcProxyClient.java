package org.wiitht.wii.dex.trail;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.wiitht.wii.dex.proxy.WriteQueue;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * @Author tanghong
 * @Date 18-8-21-下午5:19
 * @Version 1.0
 */
public class GrpcProxyClient {

    private static ProxyWriteQueue queue;

    public static void start(WriteQueue writeQueue){
        if (Objects.isNull(queue)){
            EventLoopGroup group = new NioEventLoopGroup();
            try {
                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(group).channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        GrpcProxyClientHandler handler = GrpcProxyClientHandler.createHandler(writeQueue);
                        pipeline.addLast("handler", handler);
                    }
                });
                bootstrap.register();
                ChannelFuture future = bootstrap.connect(new InetSocketAddress("localhost", 50051)).sync();
                Channel channel = future.channel();
                queue = new ProxyWriteQueue(channel);
                //bootstrap.connect(new InetSocketAddress("localhost", 50051)).sync();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public static void sendHeader(SendHeaderCommand command){
        queue.enqueue(command, true);
    }

    public static void sendFrame(SendFrameCommand command){
        queue.enqueue(command, true);
    }
    
      public static void sendData(SendDataCommand command){
        queue.enqueue(command, true);
    }

}
