package org.wiitht.wii.core.starter.consul;

import com.ecwid.consul.v1.health.model.HealthService;
import org.wiitht.wii.core.internal.discovery.ServiceInstance;

import java.net.URI;
import java.util.Map;

/**
 * @Author wii
 * @Date 18-12-27-下午4:12
 * @Version 1.0
 */
public class ConsulInstance implements ServiceInstance {
    private HealthService healthService;

    public ConsulInstance(HealthService healthService){
        this.healthService = healthService;
    }

    @Override
    public String serviceId() {
        return healthService.getService().getId();
    }

    @Override
    public String host() {
        return healthService.getService().getAddress();
    }

    @Override
    public int port() {
        return healthService.getService().getPort();
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public URI uri() {
        return null;
    }

    @Override
    public Map<String, String> metadata() {
        return null;
    }

    @Override
    public String appName() {
        return healthService.getService().getService();
    }

    @Override
    public String serverGroup() {
        return null;
    }

    public HealthService getHealthService() {
        return healthService;
    }

    public void setHealthService(HealthService healthService) {
        this.healthService = healthService;
    }
}