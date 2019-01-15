package org.wiitht.wii.eel.mesh.client;

import com.google.common.collect.MapMaker;
import com.linecorp.armeria.client.*;
import com.linecorp.armeria.client.pool.DefaultKeyedChannelPool;
import com.linecorp.armeria.client.pool.KeyedChannelPool;
import com.linecorp.armeria.client.pool.KeyedChannelPoolHandler;
import com.linecorp.armeria.client.pool.PoolKey;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.Scheme;
import com.linecorp.armeria.common.SerializationFormat;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.util.ReleasableHolder;
import com.linecorp.armeria.internal.TransportType;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Objects.requireNonNull;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:04
 * @Version 1.0
 */
public class HttpClientFactory extends AbstractClientFactory {

    private static final Set<Scheme> SUPPORTED_SCHEMES =
            Arrays.stream(SessionProtocol.values())
                    .map(p -> Scheme.of(SerializationFormat.NONE, p))
                    .collect(toImmutableSet());

    private static final Predicate<Channel> POOL_HEALTH_CHECKER =
            ch -> ch.isActive() && HttpSession.get(ch).isActive();

    private final EventLoopGroup workerGroup;
    private final boolean shutdownWorkerGroupOnClose;
    private final Bootstrap baseBootstrap;
    private final Consumer<? super SslContextBuilder> sslContextCustomizer;
    private final int initialHttp2ConnectionWindowSize;
    private final int initialHttp2StreamWindowSize;
    private final int http2MaxFrameSize;
    private final int maxHttp1InitialLineLength;
    private final int maxHttp1HeaderSize;
    private final int maxHttp1ChunkSize;
    private final long idleTimeoutMillis;
    private final boolean useHttp2Preface;
    private final boolean useHttp1Pipelining;
    private final HttpClientFactory.ConnectionPoolListenerImpl connectionPoolListener;
    private MeterRegistry meterRegistry;

    private final ConcurrentMap<EventLoop, KeyedChannelPool<PoolKey>> pools = new MapMaker().weakKeys()
            .makeMap();
    private final HttpClientDelegate clientDelegate;

    private final EventLoopScheduler eventLoopScheduler;
    private final Supplier<EventLoop> eventLoopSupplier =
            () -> RequestContext.mapCurrent(RequestContext::eventLoop, () -> eventLoopGroup().next());

    HttpClientFactory(
            EventLoopGroup workerGroup, boolean shutdownWorkerGroupOnClose,
            Map<ChannelOption<?>, Object> channelOptions,
            Consumer<? super SslContextBuilder> sslContextCustomizer,
            Function<? super EventLoopGroup,
                    ? extends AddressResolverGroup<? extends InetSocketAddress>> addressResolverGroupFactory,
            int initialHttp2ConnectionWindowSize, int initialHttp2StreamWindowSize, int http2MaxFrameSize,
            int maxHttp1InitialLineLength, int maxHttp1HeaderSize, int maxHttp1ChunkSize,
            long idleTimeoutMillis, boolean useHttp2Preface, boolean useHttp1Pipelining,
            KeyedChannelPoolHandler<? super PoolKey> connectionPoolListener, MeterRegistry meterRegistry) {

        @SuppressWarnings("unchecked")
        final AddressResolverGroup<InetSocketAddress> addressResolverGroup =
                (AddressResolverGroup<InetSocketAddress>) addressResolverGroupFactory.apply(workerGroup);

        final Bootstrap baseBootstrap = new Bootstrap();
        baseBootstrap.channel(TransportType.socketChannelType(workerGroup));
        baseBootstrap.resolver(addressResolverGroup);

        channelOptions.forEach((option, value) -> {
            @SuppressWarnings("unchecked")
            final ChannelOption<Object> castOption = (ChannelOption<Object>) option;
            baseBootstrap.option(castOption, value);
        });

        this.workerGroup = workerGroup;
        this.shutdownWorkerGroupOnClose = shutdownWorkerGroupOnClose;
        this.baseBootstrap = baseBootstrap;
        this.sslContextCustomizer = sslContextCustomizer;
        this.initialHttp2ConnectionWindowSize = initialHttp2ConnectionWindowSize;
        this.initialHttp2StreamWindowSize = initialHttp2StreamWindowSize;
        this.http2MaxFrameSize = http2MaxFrameSize;
        this.maxHttp1InitialLineLength = maxHttp1InitialLineLength;
        this.maxHttp1HeaderSize = maxHttp1HeaderSize;
        this.maxHttp1ChunkSize = maxHttp1ChunkSize;
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.useHttp2Preface = useHttp2Preface;
        this.useHttp1Pipelining = useHttp1Pipelining;
        this.connectionPoolListener = new HttpClientFactory.ConnectionPoolListenerImpl(connectionPoolListener);
        this.meterRegistry = meterRegistry;

        clientDelegate = new HttpClientDelegate(this, addressResolverGroup);
        eventLoopScheduler = new EventLoopScheduler(workerGroup);
    }

    /**
     * Returns a new {@link Bootstrap} whose {@link ChannelFactory}, {@link AddressResolverGroup} and
     * socket options are pre-configured.
     */
    Bootstrap newBootstrap() {
        return baseBootstrap.clone();
    }

    Consumer<? super SslContextBuilder> sslContextCustomizer() {
        return sslContextCustomizer;
    }

    int initialHttp2ConnectionWindowSize() {
        return initialHttp2ConnectionWindowSize;
    }

    int initialHttp2StreamWindowSize() {
        return initialHttp2StreamWindowSize;
    }

    int http2MaxFrameSize() {
        return http2MaxFrameSize;
    }

    int maxHttp1InitialLineLength() {
        return maxHttp1InitialLineLength;
    }

    int maxHttp1HeaderSize() {
        return maxHttp1HeaderSize;
    }

    int maxHttp1ChunkSize() {
        return maxHttp1ChunkSize;
    }

    long idleTimeoutMillis() {
        return idleTimeoutMillis;
    }

    boolean useHttp2Preface() {
        return useHttp2Preface;
    }

    boolean useHttp1Pipelining() {
        return useHttp1Pipelining;
    }

    KeyedChannelPoolHandler<? super PoolKey> connectionPoolListener() {
        return connectionPoolListener;
    }

    @Override
    public Set<Scheme> supportedSchemes() {
        return SUPPORTED_SCHEMES;
    }

    @Override
    public EventLoopGroup eventLoopGroup() {
        return workerGroup;
    }

    @Override
    public Supplier<EventLoop> eventLoopSupplier() {
        return eventLoopSupplier;
    }

    @Override
    public ReleasableHolder<EventLoop> acquireEventLoop(Endpoint endpoint) {
        return eventLoopScheduler.acquire(endpoint);
    }

    @Override
    public MeterRegistry meterRegistry() {
        return meterRegistry;
    }

    @Override
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = requireNonNull(meterRegistry, "meterRegistry");
    }


    @Override
    public <T> T newClient(URI uri, Class<T> clientType, ClientOptions options) {
        final Scheme scheme = validateScheme(uri);

        validateClientType(clientType);

        final Client<HttpRequest, HttpResponse> delegate = options.decoration().decorate(
                HttpRequest.class, HttpResponse.class, clientDelegate);

        if (clientType == Client.class) {
            @SuppressWarnings("unchecked")
            final T castClient = (T) delegate;
            return castClient;
        }

        final Endpoint endpoint = newEndpoint(uri);

        if (clientType == HttpClient.class) {
            final HttpClient client = newHttpClient(uri, scheme, endpoint, options, delegate);

            @SuppressWarnings("unchecked")
            final T castClient = (T) client;
            return castClient;
        } else {
            throw new IllegalArgumentException("unsupported client type: " + clientType.getName());
        }
    }

    @Override
    public <T> Optional<ClientBuilderParams> clientBuilderParams(T client) {
        return Optional.empty();
    }

    private DefaultHttpClient newHttpClient(URI uri, Scheme scheme, Endpoint endpoint, ClientOptions options,
                                            Client<HttpRequest, HttpResponse> delegate) {
        return new DefaultHttpClient(
                new DefaultClientBuilderParams(this, uri, HttpClient.class, options),
                delegate, meterRegistry, scheme.sessionProtocol(), endpoint);
    }

    private static void validateClientType(Class<?> clientType) {
        if (clientType != HttpClient.class && clientType != Client.class) {
            throw new IllegalArgumentException(
                    "clientType: " + clientType +
                            " (expected: " + HttpClient.class.getSimpleName() + " or " +
                            Client.class.getSimpleName() + ')');
        }
    }

    @Override
    public void close() {
        connectionPoolListener.setClosed();

        for (final Iterator<KeyedChannelPool<PoolKey>> i = pools.values().iterator(); i.hasNext();) {
            i.next().close();
            i.remove();
        }

        if (shutdownWorkerGroupOnClose) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }
    }

    KeyedChannelPool<PoolKey> pool(EventLoop eventLoop) {
        final KeyedChannelPool<PoolKey> pool = pools.get(eventLoop);
        if (pool != null) {
            return pool;
        }

        return pools.computeIfAbsent(eventLoop, e -> {
            final Function<PoolKey, Future<Channel>> channelFactory =
                    new HttpSessionChannelFactory(this, eventLoop);

            @SuppressWarnings("unchecked")
            final KeyedChannelPoolHandler<PoolKey> handler =
                    (KeyedChannelPoolHandler<PoolKey>) connectionPoolListener();

            return new DefaultKeyedChannelPool<>(
                    eventLoop, channelFactory, POOL_HEALTH_CHECKER, handler, true);
        });
    }

    private static final class ConnectionPoolListenerImpl implements KeyedChannelPoolHandler<PoolKey> {

        private final KeyedChannelPoolHandler<? super PoolKey> connectionPoolListener;
        private volatile boolean closed;

        ConnectionPoolListenerImpl(KeyedChannelPoolHandler<? super PoolKey> connectionPoolListener) {
            this.connectionPoolListener = connectionPoolListener;
        }

        @Override
        public void channelCreated(PoolKey key, Channel ch) throws Exception {
            if (closed) {
                ch.close();
                return;
            }

            connectionPoolListener.channelCreated(key, ch);
        }

        @Override
        public void channelAcquired(PoolKey key, Channel ch) throws Exception {
            connectionPoolListener.channelAcquired(key, ch);
        }

        @Override
        public void channelReleased(PoolKey key, Channel ch) throws Exception {
            connectionPoolListener.channelReleased(key, ch);
        }

        @Override
        public void channelClosed(PoolKey key, Channel ch) throws Exception {
            connectionPoolListener.channelClosed(key, ch);
        }

        void setClosed() {
            closed = true;
        }
    }
}
