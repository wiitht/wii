package org.wiitht.wii.core.proxy;

import org.wiitht.wii.core.internal.manage.ManageConfiguration;
import org.wiitht.wii.core.internal.manage.ManageContext;
import org.wiitht.wii.core.proxy.route.RouterManager;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Headers;

/**
 * @Author wii
 * @Date 18-12-13-下午4:55
 * @Version 1.0
 */
public class ProxyH2Exchange {
    private ManageContext manageContext;
    private RouterManager routerManager;

    public static ProxyH2Exchange newExchange(ManageConfiguration configuration){
        return new ProxyH2Exchange(configuration);
    }

    private ProxyH2Exchange(ManageConfiguration configuration){
        this.manageContext = ManageContext.init(configuration);
    }

    public void handleHeader(ChannelHandlerContext ctx, int streamId, Http2Headers headers){
        this.routerManager = RouterManager.init(manageContext);
        this.routerManager.handleHeader(ctx, streamId, headers);
    }

    public void handleData(int streamId, ByteBuf data, int padding, boolean endOfStream){
        this.routerManager.handleData(streamId, data, padding, endOfStream);
    }
}