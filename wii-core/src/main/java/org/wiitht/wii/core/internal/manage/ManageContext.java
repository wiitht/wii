package org.wiitht.wii.core.internal.manage;

/**
 * 管理控制的上下文
 */
import org.wiitht.wii.core.common.config.RouteProperties;
import org.wiitht.wii.core.internal.discovery.IDiscovery;

import java.util.Map;

/**
 * @Author wii
 * @Date 18-12-24-下午3:40
 * @Version 1.0
 */
public class ManageContext {
    private RouteTable routeTable;
    private IDiscovery iDiscovery;

    public class RouteTable{
        private String prefix;
        private Map<String, RouteProperties.Route> map;

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public Map<String, RouteProperties.Route> getMap() {
            return map;
        }

        public void setMap(Map<String, RouteProperties.Route> map) {
            this.map = map;
        }
    }

    public void setiDiscovery(IDiscovery iDiscovery) {
        this.iDiscovery = iDiscovery;
    }

    public RouteTable getRouteTable(){
        return this.routeTable;
    }

    public IDiscovery getiDiscovery(){
        return this.iDiscovery;
    }

    public void initRouteTable(ManageConfiguration configuration){
        RouteTable routeTable = new RouteTable();
        routeTable.setMap(configuration.getRouteProperties().getRoutes());
        routeTable.setPrefix(configuration.getRouteProperties().getPrefix());
        this.routeTable = routeTable;
    }

    public static ManageContext init(ManageConfiguration configuration){
        ManageContext context = new ManageContext();
        context.initRouteTable(configuration);
        context.setiDiscovery(configuration.getiDiscovery());
        return context;
    }

}