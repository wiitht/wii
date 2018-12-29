package org.wiitht.wii.core.starter.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.health.model.HealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wiitht.wii.core.internal.discovery.IDiscovery;
import org.wiitht.wii.core.internal.discovery.ServiceInstance;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author wii
 * @Date 18-12-27-下午4:11
 * @Version 1.0
 */
public class ConsulDiscovery implements IDiscovery {
    private ConsulClient consulClient;
    private static final Logger LOG = LoggerFactory.getLogger(ConsulDiscovery.class);

    public ConsulDiscovery(ConsulClient consulClient){
        this.consulClient = consulClient;
    }

    @Override
    public String description() {
        return null;
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        Response<List<HealthService>> response = consulClient.getHealthServices(serviceId, null, false, QueryParams.DEFAULT, "");
        if (response.getValue() != null){
            return response.getValue().stream().map(ConsulInstance::new)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public List<ServiceInstance> getServices() {
        return null;
    }
}