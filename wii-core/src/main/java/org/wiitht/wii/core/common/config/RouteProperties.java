package org.wiitht.wii.core.common.config;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Author wii
 * @Date 18-12-24-下午7:37
 * @Version 1.0
 */
public class RouteProperties {

    private String prefix="";

    private Map<String, Route> routes = new LinkedHashMap<>();

    public static class Route{
        private String id;

        private String path;

        private String serviceId;

        private String url;

        private boolean stripPrefix=true;

        private Boolean retryable;

        private Set<String> sensitiveHeaders = new LinkedHashSet<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public boolean isStripPrefix() {
            return stripPrefix;
        }

        public void setStripPrefix(boolean stripPrefix) {
            this.stripPrefix = stripPrefix;
        }

        public Boolean getRetryable() {
            return retryable;
        }

        public void setRetryable(Boolean retryable) {
            this.retryable = retryable;
        }

        public Set<String> getSensitiveHeaders() {
            return sensitiveHeaders;
        }

        public void setSensitiveHeaders(Set<String> sensitiveHeaders) {
            this.sensitiveHeaders = sensitiveHeaders;
        }
    }

    public static class Host{

        private int maxTotalConnections=200;

        private int maxPerRouteConnections=20;

        private int socketTimeoutMillis = 10000;

        private int connectionTimeoutMillis=2000;

        private int connectionRequestTimeMillis=-1;

        private long timeToLive=-1;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Map<String, Route> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, Route> routes) {
        this.routes = routes;
    }
}