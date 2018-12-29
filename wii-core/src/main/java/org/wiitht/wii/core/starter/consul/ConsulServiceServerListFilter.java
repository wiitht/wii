package org.wiitht.wii.core.starter.consul;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerListFilter;
import com.netflix.loadbalancer.ZoneAffinityServerListFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author wii
 * @Date 18-12-28-上午11:21
 * @Version 1.0
 */
public class ConsulServiceServerListFilter<T extends Server> implements ServerListFilter<T> {

    @Override
    public List<T> getFilteredListOfServers(List<T> servers) {
        List<T> list = new ArrayList<>();
        for (T server: servers){
            if (server instanceof ConsulServer){
                ConsulServer consulServer = (ConsulServer)server;
                if (consulServer.isPassingChecks()){
                    list.add(server);
                }
            } else {
                list.add(server);
            }
        }
        return list;
    }

}