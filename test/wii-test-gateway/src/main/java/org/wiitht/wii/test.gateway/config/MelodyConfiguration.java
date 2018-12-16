package org.wiitht.wii.test.gateway.config;

import net.bull.javamelody.MonitoredWithAnnotationPointcut;
import net.bull.javamelody.MonitoringFilter;
import net.bull.javamelody.MonitoringSpringAdvisor;
import net.bull.javamelody.Parameter;
import org.apache.catalina.SessionListener;
import org.apache.catalina.authenticator.SingleSignOnListener;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

@Component
//@ImportResource(Array("classpath:net/bull/javamelody/bas-spring.xml"))
@SuppressWarnings("javadoc")
public class MelodyConfiguration implements ServletContextInitializer {

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        //servletContext.addListener(new SingleSignOnListener("w3erwr"));
    }

    @Bean
    public FilterRegistrationBean registrationBean() {
        //ip:port/marketing/bas
        FilterRegistrationBean javaMelody = new FilterRegistrationBean();
        javaMelody.setFilter(new MonitoringFilter());
        javaMelody.setAsyncSupported(true);
        javaMelody.setName("melody");
        javaMelody.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        javaMelody.addInitParameter(Parameter.LOG.getCode(), "true");
        javaMelody.addInitParameter("bas-path", "/gateway/bas");
        //javaMelody.addUrlPatterns("/*")
        return javaMelody;
    }

    @Bean
    public MonitoringSpringAdvisor monitoringAdvisor(){
        MonitoringSpringAdvisor msa = new MonitoringSpringAdvisor();
        msa.setPointcut(new MonitoredWithAnnotationPointcut());
        return msa;
    }
}