package org.wiitht.wii.core.common.config;

/**
 * @Author wii
 * @Date 18-12-25-下午4:13
 * @Version 1.0
 */
public class ServerProperties {
    private int port;

    public class Application{
        private String name;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}