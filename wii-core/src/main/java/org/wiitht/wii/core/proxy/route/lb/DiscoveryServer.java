package org.wiitht.wii.core.proxy.route.lb;

import org.wiitht.wii.core.internal.discovery.ServiceInstance;
import com.netflix.loadbalancer.Server;

import java.util.Map;

/**
 * @Author wii
 * @Date 18-12-27-下午2:07
 * @Version 1.0
 */
public class DiscoveryServer extends Server {

    private final ServiceInstance service;
    private Map<String, String> metadata;
    private MetaInfo metaInfo;

    public DiscoveryServer(ServiceInstance serviceInstance) {
        super(serviceInstance.host(), serviceInstance.port());
        this.service = serviceInstance;
        this.metadata = serviceInstance.metadata();
        this.metaInfo = new MetaInfo(){

            @Override
            public String getAppName() {
                return serviceInstance.appName();
            }

            @Override
            public String getServerGroup() {
                return serviceInstance.serverGroup();
            }

            @Override
            public String getServiceIdForDiscovery() {
                return null;
            }

            @Override
            public String getInstanceId() {
                return serviceInstance.serviceId();
            }
        };
    }

    public ServiceInstance getService() {
        return service;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    @Override
    public MetaInfo getMetaInfo() {
        return metaInfo;
    }

    public void setMetaInfo(MetaInfo metaInfo) {
        this.metaInfo = metaInfo;
    }
}