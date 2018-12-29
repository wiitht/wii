package org.wiitht.wii.core.proxy.route.lb;

import org.wiitht.wii.core.internal.discovery.IDiscovery;
import org.wiitht.wii.core.starter.consul.ConsulPing;
import org.wiitht.wii.core.starter.consul.ConsulServer;
import org.wiitht.wii.core.starter.consul.ConsulServiceServerListFilter;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.loadbalancer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author wii
 * @Date 18-12-27-下午2:08
 * @Version 1.0
 */
public class LoadBalancePicker {

    private static final int DEFAULT_CONNECTION_TIMEOUT = 1000;
    private static final int DEFAULT_READ_TIMEOUT=1000;
    private ZoneAwareLoadBalancer<Server> zoneAwareLoadBalancer;
    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancePicker.class);

    public static LoadBalancePicker newBuilder(){
        return new LoadBalancePicker();
    }

    public LoadBalancePicker init(IDiscovery discovery, String serviceId){
        DefaultClientConfigImpl configIml = new DefaultClientConfigImpl();
        configIml.loadProperties(serviceId);
        configIml.set(CommonClientConfigKey.ConnectTimeout, DEFAULT_CONNECTION_TIMEOUT);
        configIml.set(CommonClientConfigKey.ReadTimeout, DEFAULT_READ_TIMEOUT);
        ZoneAvoidanceRule rule = new ZoneAvoidanceRule();
        rule.initWithNiwsConfig(configIml);

        AbstractServerList<Server> serverList = new DiscoveryServerList(discovery);
        serverList.initWithNiwsConfig(configIml);

        ConsulServiceServerListFilter<Server> serverListFilter = new ConsulServiceServerListFilter<Server>();
        //serverListFilter.initWithNiwsConfig(configIml);

        this.zoneAwareLoadBalancer = new ZoneAwareLoadBalancer<Server>(
                configIml, rule, new ConsulPing(), serverList, serverListFilter,
                new PollingServerListUpdater()
        );
        return this;
    }

    public Server pick(){
        return this.zoneAwareLoadBalancer.chooseServer();
    }
}