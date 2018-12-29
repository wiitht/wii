package org.wiitht.wii.core.proxy.route.lb;

import org.wiitht.wii.core.internal.discovery.IDiscovery;
import org.wiitht.wii.core.internal.discovery.ServiceInstance;
import org.wiitht.wii.core.starter.consul.ConsulServer;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.Server;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author wii
 * @Date 18-12-27-下午2:23
 * @Version 1.0
 */
public class DiscoveryServerList extends AbstractServerList<Server> {
    private final IDiscovery discovery;
    private String serviceId;

    public DiscoveryServerList(IDiscovery discovery){
        this.discovery = discovery;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig iClientConfig) {
        this.serviceId = iClientConfig.getClientName();
    }

    @Override
    public List<Server> getInitialListOfServers() {
        return getServer();
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        return getServer();
    }

    private List<Server> getServer(){
        List<ServiceInstance> list = this.discovery.getInstances(serviceId);
        if (list != null){
            return list.stream().map(m -> {
                return (Server) new ConsulServer(m);
            }).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public IDiscovery getClient(){
        return this.discovery;
    }

    public String getServiceId(){
        return this.serviceId;
    }
}