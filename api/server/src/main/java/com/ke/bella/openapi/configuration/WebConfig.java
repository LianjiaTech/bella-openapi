package com.ke.bella.openapi.configuration;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.intercept.AuthorizationInterceptor;
import com.ke.bella.openapi.intercept.ConcurrentStartInterceptor;
import com.ke.bella.openapi.intercept.MonthQuotaInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    public static final List<String> endpointPathPatterns = Arrays.stream(EntityConstants.SystemBasicEndpoint.values())
            .map(EntityConstants.SystemBasicEndpoint::getEndpoint).collect(Collectors.toList());
    @Autowired
    private ConcurrentStartInterceptor concurrentStartInterceptor;
    @Autowired
    private AuthorizationInterceptor authorizationInterceptor;
    @Autowired
    private MonthQuotaInterceptor monthQuotaInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/console/**")
                .addPathPatterns("/v*/**")
                .order(100);
        registry.addInterceptor(monthQuotaInterceptor)
                .addPathPatterns(endpointPathPatterns)
                .order(110);
        registry.addInterceptor(concurrentStartInterceptor);
    }
    
    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
            .defaultContentType(MediaType.APPLICATION_JSON)
            .ignoreAcceptHeader(true);
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 设置消息缓冲区大小为 256KB
        container.setMaxTextMessageBufferSize(256 * 1024);
        container.setMaxBinaryMessageBufferSize(256 * 1024);
        return container;
    }
}
