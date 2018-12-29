package org.wiitht.wii.core.internal.message;

import io.grpc.internal.GrpcUtil;
import io.netty.handler.codec.http2.Http2Settings;

/**
 * @Author wii
 * @Date 18-12-13-下午7:32
 * @Version 1.0
 */
public class GrpcHttp2Messages {
    /**
     * 1MiB
     */
    private static final int DEFAULT_FLOW_CONTROL_WINDOW = 1048576;

    public static Http2Settings getHttp2Settings(){
        Http2Settings http2Settings = new Http2Settings();
        http2Settings.initialWindowSize(DEFAULT_FLOW_CONTROL_WINDOW);
        http2Settings.maxConcurrentStreams(Integer.MAX_VALUE);
        http2Settings.maxHeaderListSize(GrpcUtil.DEFAULT_MAX_HEADER_LIST_SIZE);
        return http2Settings;
    }
}