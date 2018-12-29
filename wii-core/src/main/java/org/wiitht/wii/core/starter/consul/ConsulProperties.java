package org.wiitht.wii.core.starter.consul;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author wii
 * @Date 18-12-27-下午4:05
 * @Version 1.0
 */
public class ConsulProperties {
    private String serviceId;
    private String checkId;
    private String checkName;
    private String serviceName;
    private String ttls="2s";
    private String timeout="10s";
    private String host;
    private int port;
    private String registerUrl;
    private String aclToken;
    private List<String> tags = new ArrayList();

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getCheckId() {
        return checkId;
    }

    public void setCheckId(String checkId) {
        this.checkId = checkId;
    }

    public String getCheckName() {
        return checkName;
    }

    public void setCheckName(String checkName) {
        this.checkName = checkName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getTtls() {
        return ttls;
    }

    public String getRegisterUrl() {
        return registerUrl;
    }

    public void setRegisterUrl(String registerUrl) {
        this.registerUrl = registerUrl;
    }

    public String getAclToken() {
        return aclToken;
    }

    public void setAclToken(String aclToken) {
        this.aclToken = aclToken;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public void setTtls(String ttls) {
        this.ttls = ttls;
    }

    public String getTimeout() {
        return timeout;
    }

    public void setTimeout(String timeout) {
        this.timeout = timeout;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
