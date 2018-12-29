package org.wiitht.wii.core.starter.consul;

import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.Server;
/**
 * @Author wii
 * @Date 18-12-28-上午10:51
 * @Version 1.0
 */
public class ConsulPing implements IPing {

    @Override
    public boolean isAlive(Server server) {
        if (server instanceof ConsulServer){
            ConsulServer consulServer = (ConsulServer) server;
            return consulServer.isPassingChecks();
        }
        return true;
    }
}