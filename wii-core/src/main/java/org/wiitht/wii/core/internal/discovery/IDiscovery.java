package org.wiitht.wii.core.internal.discovery;

import java.util.List;

/**
 * @Author wii
 * @Date 18-12-24-下午5:17
 * @Version 1.0
 */
public interface IDiscovery {

    String description();

    List<ServiceInstance> getInstances(String serviceId);

    List<ServiceInstance> getServices();

}