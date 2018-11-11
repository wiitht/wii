package org.wiitht.wii.dex.test;

import org.wiitht.wii.dex.proxy.NettyProxyHandler;
import io.grpc.internal.*;
import io.netty.channel.*;

import java.util.concurrent.TimeUnit;

import static io.grpc.internal.GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE;
import static io.grpc.internal.GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIMEOUT_NANOS;
import static io.grpc.internal.GrpcUtil.DEFAULT_SERVER_KEEPALIVE_TIME_NANOS;
import static io.grpc.netty.NettyServerBuilder.DEFAULT_FLOW_CONTROL_WINDOW;
/**
 * @Author tanghong
 * @Date 18-8-14-下午2:46
 * @Version 1.0
 */
public class HelloHandler extends SimpleChannelInboundHandler<Object>{
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println(msg);
    }
    private static final long MAX_CONNECTION_IDLE_NANOS_DISABLED = Long.MAX_VALUE;
    private static final long MAX_CONNECTION_AGE_NANOS_DISABLED = Long.MAX_VALUE;
    private static final long MAX_CONNECTION_AGE_GRACE_NANOS_INFINITE = Long.MAX_VALUE;

    private static int maxConcurrentCallsPerConnection = Integer.MAX_VALUE;
    private static int flowControlWindow = DEFAULT_FLOW_CONTROL_WINDOW;
    private static int maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
    private static int maxHeaderListSize = GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE;
    private static long keepAliveTimeInNanos =  DEFAULT_SERVER_KEEPALIVE_TIME_NANOS;
    private static long keepAliveTimeoutInNanos = DEFAULT_SERVER_KEEPALIVE_TIMEOUT_NANOS;
    private static long maxConnectionIdleInNanos = MAX_CONNECTION_IDLE_NANOS_DISABLED;
    private static long maxConnectionAgeInNanos = MAX_CONNECTION_AGE_NANOS_DISABLED;
    private static long maxConnectionAgeGraceInNanos = MAX_CONNECTION_AGE_GRACE_NANOS_INFINITE;
    private static boolean permitKeepAliveWithoutCalls;
    private static long permitKeepAliveTimeInNanos = TimeUnit.MINUTES.toNanos(5);

    public static NettyProxyHandler createHandler(ChannelPromise channelDone) {
        return NettyProxyHandler.newHandler(
                null,
                channelDone,
                null,
                null,
                Integer.MAX_VALUE,
                flowControlWindow,
                maxHeaderListSize,
                maxMessageSize,
                keepAliveTimeInNanos,
                keepAliveTimeoutInNanos,
                maxConnectionIdleInNanos,
                maxConnectionAgeInNanos,
                maxConnectionAgeGraceInNanos,
                permitKeepAliveWithoutCalls,
                permitKeepAliveTimeInNanos);

    }

}
