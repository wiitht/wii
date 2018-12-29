package org.wiitht.wii.core.proxy.route;

import org.wiitht.wii.core.common.config.RouteProperties;
import org.wiitht.wii.core.internal.discovery.IDiscovery;
import org.wiitht.wii.core.internal.manage.Deliverer;
import org.wiitht.wii.core.internal.manage.ManageContext;
import org.wiitht.wii.core.internal.manage.Receiver;
import org.wiitht.wii.core.proxy.route.lb.LoadBalancePicker;
import com.netflix.loadbalancer.Server;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author wii
 * @Date 18-12-24-下午3:27
 * @Version 1.0
 * init:
 * 1) 负载均衡器(添加服务发现)
 */
public class RouterManager {
    private RouterContext routerContext;
    private Deliverer deliverer;
    private Server server;
    /**
     * pickerCache:
     * 1) 根据serviceId将picker添加到缓存;
     * 2) 路由表刷新的时候需要clear pickerCache;
     */
    private static final Map<String, LoadBalancePicker> pickerCache = new HashMap<String, LoadBalancePicker>();
    private static final Logger LOG = LoggerFactory.getLogger(RouterManager.class);

    /**
     * HTTP2 HEADER handle
     * @param ctx
     * @param streamId
     * @param headers
     */
    public void handleHeader(ChannelHandlerContext ctx, int streamId, Http2Headers headers){
        IDiscovery discovery = routerContext.getManageContext().getiDiscovery();
        String reqUrl = headers.path().toString();
        String serviceId = findServiceId(reqUrl);
        LoadBalancePicker picker = pickerCache.get(serviceId);
        if (picker != null){
            this.server = picker.pick();
        } else {
            LoadBalancePicker loadBalancePicker = LoadBalancePicker.newBuilder().init(discovery, serviceId);
            pickerCache.put(serviceId, loadBalancePicker);
            this.server = loadBalancePicker.pick();
        }
        this.deliverer = new Deliverer();
        Receiver receiver = new Receiver(ctx);
        this.deliverer.setReceiver(receiver);
        this.deliverer.start(this.server.getHost(), this.server.getPort());
        this.deliverer.sendHeaderCommand(streamId, headers);
    }

    /**
     * HTTP2 DATA handle
     * @param streamId
     * @param data
     * @param padding
     * @param endOfStream
     */
    public void handleData(int streamId, ByteBuf data, int padding, boolean endOfStream){
        this.deliverer.sendDataCommand(streamId, data, padding, endOfStream);
    }

    public static RouterManager init(ManageContext manageContext){
        RouterManager routerManager = new RouterManager();
        routerManager.setRouterContext(new RouterContext(manageContext));
        return routerManager;
    }

    private String findServiceId(String path){
        ManageContext.RouteTable routeTable = routerContext.getManageContext().getRouteTable();
        for (RouteProperties.Route route: routeTable.getMap().values()){
            if (path.contains(route.getPath())){
                return route.getServiceId();
            }
        }
        return null;
    }

    public RouterContext getRouterContext() {
        return routerContext;
    }

    public void setRouterContext(RouterContext routerContext) {
        this.routerContext = routerContext;
    }

    public Deliverer getDeliverer() {
        return deliverer;
    }

    public void setDeliverer(Deliverer deliverer) {
        this.deliverer = deliverer;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }
}