package org.wiitht.wii.core.starter.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewCheck;
import com.ecwid.consul.v1.agent.model.NewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author wii
 * @Date 18-12-27-下午4:48
 * @Version 1.0
 */
public class ConsulBuilder {
    private ConsulProperties consulProperties;
    private ConsulClient consulClient;
    private static final Logger LOG = LoggerFactory.getLogger(ConsulBuilder.class);

    public static ConsulBuilder newBuilder(){
        return new ConsulBuilder();
    }

    public ConsulBuilder properties(ConsulProperties properties){
        this.consulProperties = properties;
        return this;
    }

    public ConsulBuilder register(){
        NewService newService = new NewService();
        newService.setId(consulProperties.getServiceId());
        newService.setName(consulProperties.getServiceName());
        newService.setPort(consulProperties.getPort());
        newService.setAddress(consulProperties.getHost());

        NewCheck newCheck = new NewCheck();
        newCheck.setName(consulProperties.getCheckName());
        newCheck.setTtl(consulProperties.getTtls());
        newCheck.setTimeout(consulProperties.getTimeout());
        newCheck.setId(consulProperties.getCheckId());
        ConsulClient consulClient = new ConsulClient(consulProperties.getRegisterUrl());
        consulClient.agentServiceRegister(newService);
        consulClient.agentCheckRegister(newCheck);
        this.consulClient = consulClient;
        return this;
    }

    public ConsulDiscovery report(){
        ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                //LOG.info("upload service info to consul center!");
                consulClient.agentCheckPass(consulProperties.getCheckId(), "hello consul");
            }
        }, 1, 1, TimeUnit.SECONDS);
        return new ConsulDiscovery(this.consulClient);
    }

}