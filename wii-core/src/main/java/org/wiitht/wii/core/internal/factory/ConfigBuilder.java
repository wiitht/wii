package org.wiitht.wii.core.internal.factory;

import org.wiitht.wii.core.common.config.RouteProperties;
import org.wiitht.wii.core.internal.discovery.IDiscovery;
import org.wiitht.wii.core.internal.manage.ManageConfiguration;

import java.util.Map;

/**
 * @Author wii
 * @Date 18-12-24-下午6:18
 * @Version 1.0
 * 1) 基础配置: 路由表,服务注册(ribbon对象)
 * 2）刷新注册: 提供一个刷新对象,回调注册一个可以刷新的对象
 * 3）
 */
public class ConfigBuilder {

    private ManageConfiguration manageConfiguration = new ManageConfiguration();

    public static ConfigBuilder newBuilder(){
        return new ConfigBuilder();
    }

    public ConfigBuilder configRoute(String prefix){
        RouteProperties routeProperties = manageConfiguration.getRouteProperties();
        routeProperties.setPrefix(prefix);
        return this;
    }

    public ConfigBuilder configRoute(String alias, RouteProperties.Route route){
        RouteProperties routeProperties = manageConfiguration.getRouteProperties();
        Map<String, RouteProperties.Route> map = routeProperties.getRoutes();
        map.put(alias, route);
        routeProperties.setRoutes(map);
        return this;
    }

    public ConfigBuilder configRoute(Map<String, RouteProperties.Route> routes){
        RouteProperties routeProperties = manageConfiguration.getRouteProperties();
        routeProperties.setRoutes(routes);
        return this;
    }

    public ConfigBuilder configDiscovery(IDiscovery discovery){
        manageConfiguration.setiDiscovery(discovery);
        return this;
    }

    public ManageConfiguration builder(){
        return manageConfiguration;
    }

}