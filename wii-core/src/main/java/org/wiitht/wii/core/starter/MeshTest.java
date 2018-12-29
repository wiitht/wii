package org.wiitht.wii.core.starter;

import org.wiitht.wii.core.common.config.RouteProperties;
import org.wiitht.wii.core.common.config.ServerProperties;
import org.wiitht.wii.core.starter.consul.ConsulProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author wii
 * @Date 18-12-27-下午5:30
 * @Version 1.0
 */
public class MeshTest {

    public static void main(String[] args){
        ServerProperties serverProperties = new ServerProperties();
        serverProperties.setPort(9001);

        ConsulProperties consulProperties = new ConsulProperties();
        consulProperties.setRegisterUrl("http://127.0.0.1:8500");
        consulProperties.setHost("127.0.0.1");
        consulProperties.setPort(9001);
        consulProperties.setCheckId("mesh_check_id");
        consulProperties.setCheckName("mesh_check_name");
        consulProperties.setServiceId("mesh_service_id");
        consulProperties.setServiceName("mesh_service_name");

        Map<String, RouteProperties.Route> routeMap = new HashMap<>(1);
        RouteProperties.Route route = new RouteProperties.Route();
        route.setPath("/helloworld.Greeter/SayHello");
        route.setServiceId("test-client");
        routeMap.put("client", route);
        MeshBuilder.newBuilder()
                .configConsul(consulProperties)
                .configRoute("wii", routeMap)
                .start(serverProperties);
    }
}