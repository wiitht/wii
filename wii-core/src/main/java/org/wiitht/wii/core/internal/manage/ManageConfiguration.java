package org.wiitht.wii.core.internal.manage;

import org.wiitht.wii.core.common.config.RouteProperties;
import org.wiitht.wii.core.internal.discovery.IDiscovery;

/**
 * @Author wii
 * @Date 18-12-25-下午4:24
 * @Version 1.0
 */
public class ManageConfiguration{

    private RouteProperties routeProperties = new RouteProperties();

    private IDiscovery iDiscovery;

    public RouteProperties getRouteProperties() {
        return routeProperties;
    }

    public void setRouteProperties(RouteProperties routeProperties) {
        this.routeProperties = routeProperties;
    }

    public IDiscovery getiDiscovery() {
        return this.iDiscovery;
    }

    public void setiDiscovery(IDiscovery iDiscovery) {
        this.iDiscovery = iDiscovery;
    }
}
