package org.wii.agency.mesh.client;

import com.linecorp.armeria.client.SessionProtocolNegotiationCache;
import com.linecorp.armeria.client.SessionProtocolNegotiationException;
import com.linecorp.armeria.client.pool.PoolKey;
import com.linecorp.armeria.common.SessionProtocol;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.util.NetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.Promise;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:07
 * @Version 1.0
 */
public class HttpSessionChannelFactory implements Function<PoolKey, Future<Channel>> {

    private final HttpClientFactory clientFactory;
    private final EventLoop eventLoop;
    private final Bootstrap baseBootstrap;
    private final int connectTimeoutMillis;
    private final Map<SessionProtocol, Bootstrap> bootstrapMap;

    HttpSessionChannelFactory(HttpClientFactory clientFactory, EventLoop eventLoop) {
        this.clientFactory = clientFactory;
        this.eventLoop = eventLoop;
        baseBootstrap = clientFactory.newBootstrap();
        baseBootstrap.group(eventLoop);
        connectTimeoutMillis = (Integer) baseBootstrap.config().options()
                .get(ChannelOption.CONNECT_TIMEOUT_MILLIS);
        bootstrapMap = Collections.synchronizedMap(new EnumMap<>(SessionProtocol.class));
    }

    @Override
    public Future<Channel> apply(PoolKey key) {
        final InetSocketAddress remoteAddress;
        try {
            remoteAddress = toRemoteAddress(key);
        } catch (UnknownHostException e) {
            return eventLoop.newFailedFuture(e);
        }

        final SessionProtocol protocol = key.sessionProtocol();

        if (SessionProtocolNegotiationCache.isUnsupported(remoteAddress, protocol)) {
            // Fail immediately if it is sure that the remote address does not support the requested protocol.
            return eventLoop.newFailedFuture(
                    new SessionProtocolNegotiationException(protocol, "previously failed negotiation"));
        }

        final Promise<Channel> sessionPromise = eventLoop.newPromise();
        connect(remoteAddress, protocol, sessionPromise);

        return sessionPromise;
    }

    private static InetSocketAddress toRemoteAddress(PoolKey key) throws UnknownHostException {
        final InetAddress inetAddr = InetAddress.getByAddress(
                key.host(), NetUtil.createByteArrayFromIpAddressString(key.ipAddr()));
        return new InetSocketAddress(inetAddr, key.port());
    }

    void connect(SocketAddress remoteAddress, SessionProtocol protocol, Promise<Channel> sessionPromise) {
        final Bootstrap bootstrap = bootstrap(protocol);
        final ChannelFuture connectFuture = bootstrap.connect(remoteAddress);

        connectFuture.addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                initSession(protocol, future, sessionPromise);
            } else {
                sessionPromise.setFailure(future.cause());
            }
        });
    }

    private Bootstrap bootstrap(SessionProtocol sessionProtocol) {
        return bootstrapMap.computeIfAbsent(sessionProtocol, sp -> {
            final Bootstrap bs = baseBootstrap.clone();
            bs.handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(new HttpClientPipelineConfigurator(clientFactory, sp));
                }
            });
            return bs;
        });
    }

    private void initSession(SessionProtocol protocol, ChannelFuture connectFuture,
                             Promise<Channel> sessionPromise) {
        assert connectFuture.isSuccess();

        final Channel ch = connectFuture.channel();
        final EventLoop eventLoop = ch.eventLoop();
        assert eventLoop.inEventLoop();

        final ScheduledFuture<?> timeoutFuture = eventLoop.schedule(() -> {
            if (sessionPromise.tryFailure(new SessionProtocolNegotiationException(
                    protocol, "connection established, but session creation timed out: " + ch))) {
                ch.close();
            }
        }, connectTimeoutMillis, TimeUnit.MILLISECONDS);

        ch.pipeline().addLast(new HttpSessionHandler(this, ch, sessionPromise, timeoutFuture));
    }
}
