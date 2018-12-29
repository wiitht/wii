package org.wiitht.wii.core.internal.discovery;

import java.net.URI;
import java.util.Map;

/**
 * @Author wii
 * @Date 18-12-24-下午5:17
 * @Version 1.0
 */
public interface ServiceInstance {
    /**
     * service id
     * @return
     */
    String serviceId();

    /**
     * host
     * @return
     */
    String host();

    /**
     * port
     * @return
     */
    int port();

    /**
     * is https or not
     * @return
     */
    boolean isSecure();

    /**
     * service uri address
     * @return
     */
    URI uri();

    /**
     * metadata
     * @return
     */
    Map<String, String> metadata();

    String appName();

    String serverGroup();

}