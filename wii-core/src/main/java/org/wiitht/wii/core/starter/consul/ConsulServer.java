package org.wiitht.wii.core.starter.consul;

import com.ecwid.consul.v1.health.model.Check;
import com.ecwid.consul.v1.health.model.HealthService;
import org.wiitht.wii.core.internal.discovery.ServiceInstance;
import org.wiitht.wii.core.proxy.route.lb.DiscoveryServer;

/**
 * @Author wii
 * @Date 18-12-28-上午11:09
 * @Version 1.0
 */
public class ConsulServer extends DiscoveryServer {

    public ConsulServer(ServiceInstance serviceInstance) {
        super(serviceInstance);
        setAlive(isPassingChecks());
    }

    public boolean isPassingChecks(){
        ServiceInstance serviceInstance = getService();
        if (serviceInstance instanceof ConsulInstance){
            ConsulInstance instance = (ConsulInstance) serviceInstance;
            HealthService healthService = instance.getHealthService();
            for (Check check: healthService.getChecks()){
                if (check.getStatus() != Check.CheckStatus.PASSING){
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}