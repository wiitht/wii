package org.wiitht.wii.core.starter;

import org.wiitht.wii.core.common.config.RouteProperties;
import org.wiitht.wii.core.common.config.ServerProperties;
import org.wiitht.wii.core.internal.factory.ConfigBuilder;
import org.wiitht.wii.core.internal.factory.ServerBuilder;
import org.wiitht.wii.core.starter.consul.ConsulBuilder;
import org.wiitht.wii.core.starter.consul.ConsulDiscovery;
import org.wiitht.wii.core.starter.consul.ConsulProperties;
import java.util.Map;

/**
 * @Author wii
 * @Date 18-12-27-下午4:05
 * @Version 1.0
 */
public class MeshBuilder {
    private ConsulBuilder consulBuilder;
    private ConfigBuilder configBuilder;

    public static MeshBuilder newBuilder(){
        return new MeshBuilder();
    }

    public MeshBuilder configConsul(ConsulProperties properties){
        this.consulBuilder = ConsulBuilder.newBuilder();
        this.consulBuilder.properties(properties);
        return this;
    }

    public MeshBuilder configRoute(String prefix, Map<String, RouteProperties.Route> routes){
        this.configBuilder = ConfigBuilder.newBuilder();
        this.configBuilder.configRoute(prefix);
        this.configBuilder.configRoute(routes);
        return this;
    }

    public void start(ServerProperties serverProperties){
        this.consulBuilder.register();
        ConsulDiscovery discovery = this.consulBuilder.report();
        this.configBuilder.configDiscovery(discovery);
        ServerBuilder
                .newBuilder()
                .setConfigBuilder(this.configBuilder)
                .start(serverProperties);
    }

}