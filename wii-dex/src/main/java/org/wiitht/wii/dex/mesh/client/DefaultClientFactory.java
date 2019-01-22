package org.wiitht.wii.dex.mesh.client;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.linecorp.armeria.client.*;
import com.linecorp.armeria.common.Scheme;
import com.linecorp.armeria.common.util.ReleasableHolder;
import io.micrometer.core.instrument.MeterRegistry;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.function.Supplier;

/**
 * @Author tanghong
 * @Date 18-10-23-下午5:36
 * @Version 1.0
 */
public class DefaultClientFactory extends AbstractClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(DefaultClientFactory.class);

    static {
        if (DefaultClientFactory.class.getClassLoader() == ClassLoader.getSystemClassLoader()) {
            Runtime.getRuntime().addShutdownHook(new Thread(ClientFactory::closeDefault));
        }
    }

    private final HttpClientFactory httpClientFactory;
    private final Map<Scheme, ClientFactory> clientFactories;
    private final List<ClientFactory> clientFactoriesToClose;

    DefaultClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;

        final List<ClientFactory> availableClientFactories = new ArrayList<>();
        availableClientFactories.add(httpClientFactory);

        Streams.stream(ServiceLoader.load(ClientFactoryProvider.class,
                DefaultClientFactory.class.getClassLoader()))
                .map(provider -> provider.newFactory(httpClientFactory))
                .forEach(availableClientFactories::add);

        final ImmutableMap.Builder<Scheme, ClientFactory> builder = ImmutableMap.builder();
        for (ClientFactory f : availableClientFactories) {
            f.supportedSchemes().forEach(s -> builder.put(s, f));
        }

        clientFactories = builder.build();
        clientFactoriesToClose = ImmutableList.copyOf(availableClientFactories).reverse();
    }

    @Override
    public Set<Scheme> supportedSchemes() {
        return clientFactories.keySet();
    }

    @Override
    public EventLoopGroup eventLoopGroup() {
        return httpClientFactory.eventLoopGroup();
    }

    @Override
    public Supplier<EventLoop> eventLoopSupplier() {
        return httpClientFactory.eventLoopSupplier();
    }

    @Override
    public ReleasableHolder<EventLoop> acquireEventLoop(Endpoint endpoint) {
        return httpClientFactory.acquireEventLoop(endpoint);
    }

    @Override
    public MeterRegistry meterRegistry() {
        return httpClientFactory.meterRegistry();
    }

    @Override
    public void setMeterRegistry(MeterRegistry meterRegistry) {
        httpClientFactory.setMeterRegistry(meterRegistry);
    }

    @Override
    public <T> T newClient(URI uri, Class<T> clientType, ClientOptions options) {
        final Scheme scheme = validateScheme(uri);
        return clientFactories.get(scheme).newClient(uri, clientType, options);
    }

    @Override
    public <T> Optional<ClientBuilderParams> clientBuilderParams(T client) {
        for (ClientFactory factory : clientFactories.values()) {
            final Optional<ClientBuilderParams> params = factory.clientBuilderParams(client);
            if (params.isPresent()) {
                return params;
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        // The global default should never be closed.
        if (this == ClientFactory.DEFAULT) {
            logger.debug("Refusing to close the default {}; must be closed via closeDefault()",
                    ClientFactory.class.getSimpleName());
            return;
        }

        doClose();
    }

    void doClose() {
        clientFactoriesToClose.forEach(ClientFactory::close);
    }
}
