package com.ke.bella.openapi.server;

import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.request.BellaInterceptor;
import com.ke.bella.openapi.request.BellaRequestFilter;
import com.ke.bella.openapi.server.intercept.AuthorizationInterceptor;
import com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor;
import com.ke.bella.openapi.utils.HttpUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import javax.annotation.PostConstruct;

@EnableConfigurationProperties(OpenapiProperties.class)
@Configuration
public class BellaServiceConfiguration {


    @PostConstruct
    public void init() {
        HttpUtils.addInterceptor(new BellaInterceptor());
    }

    @Bean
    public OpenapiClient openapiClient(OpenapiProperties properties) {
        return new OpenapiClient(properties.getHost());
    }

    @Bean
    public FilterRegistrationBean<BellaRequestFilter> bellaRequestFilter(OpenapiClient openapiClient, OpenapiProperties properties) {
        FilterRegistrationBean<BellaRequestFilter> filterRegistrationBean = new FilterRegistrationBean<>();
        BellaRequestFilter bellaRequestFilter = new BellaRequestFilter(properties.getService(), openapiClient);
        filterRegistrationBean.setFilter(bellaRequestFilter);
        filterRegistrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return filterRegistrationBean;
    }

    @Bean
    public ConcurrentStartInterceptor concurrentStartInterceptor() {
        return new ConcurrentStartInterceptor();
    }

    @Bean
    public AuthorizationInterceptor authorizationInterceptor() {
        return new AuthorizationInterceptor();
    }
}
