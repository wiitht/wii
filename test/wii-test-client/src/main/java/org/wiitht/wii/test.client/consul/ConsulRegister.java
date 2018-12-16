package org.wiitht.wii.test.client.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewCheck;
import com.ecwid.consul.v1.agent.model.NewService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

//@Configuration
//@EnableScheduling
public class ConsulRegister implements InitializingBean, DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsulRegister.class);

    private final String CHECK_ID = "test_check_id";
    private final String CHECK_NAME = "test_check";
    private final String SERVICE_ID = "test-client-consul";


    private void registerServiceToConsul() {
        LOGGER.info(">>>> Starting check consul config.");
        try {
            //组装服务信息
            NewService newService = new NewService();
            newService.setId(SERVICE_ID);
            newService.setName("tt-client");
            newService.setPort(8082);
            newService.setAddress("127.0.0.1");
            //组装健康检查信息
            NewCheck serviceCheck = new NewCheck();
            serviceCheck.setName(CHECK_NAME);
            serviceCheck.setTtl("2s");
            serviceCheck.setTimeout("10s");
            //serviceCheck.setInterval("1ms");
            //serviceCheck.setTcp("192.168.1.100:8082");
            serviceCheck.setId(CHECK_ID);
            ConsulClient client = new ConsulClient("http://127.0.0.1:8500");
            client.agentServiceRegister(newService);
            client.agentCheckRegister(serviceCheck);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        registerServiceToConsul();
    }

    @Override
    public void destroy() throws Exception {
        ConsulClient client = new ConsulClient("http://127.0.0.1:8500");
        client.agentServiceDeregister(SERVICE_ID);
    }

    @Scheduled(cron = "0/1 * * * * *")
    public void timer(){
        ConsulClient client = new ConsulClient("http://127.0.0.1:8500");
        client.agentCheckPass(CHECK_ID, "hello consul");
    }
}
