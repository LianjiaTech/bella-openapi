package com.ke.bella.openapi.configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.intercept.MonthQuotaInterceptor;
import com.ke.bella.openapi.intercept.QpsRateLimitInterceptor;
import com.ke.bella.openapi.protocol.asr.AsrRequestArgumentResolver;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private static final String PROMETHEUS_ENDPOINT = "/actuator/prometheus";
    public static final List<String> endpointPathPatterns = Arrays.stream(EntityConstants.SystemBasicEndpoint.values())
            .map(EntityConstants.SystemBasicEndpoint::getEndpoint).collect(Collectors.toList());
    @Autowired
    private MonthQuotaInterceptor monthQuotaInterceptor;

    @Autowired
    private QpsRateLimitInterceptor qpsRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

		// QPS 限流拦截器（order=109）- 在 AuthorizationInterceptor(100) 之后，MonthQuotaInterceptor(110) 之前
		registry.addInterceptor(qpsRateLimitInterceptor)
			.addPathPatterns(endpointPathPatterns)
			.order(109);

        // 月额度拦截器（order=110）
        registry.addInterceptor(monthQuotaInterceptor)
                .addPathPatterns(endpointPathPatterns)
                .order(110);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AsrRequestArgumentResolver());
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .defaultContentTypeStrategy(webRequest -> {
                    HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
                    if(request != null && PROMETHEUS_ENDPOINT.equals(request.getRequestURI())) {
                        return Collections.singletonList(MediaType.TEXT_PLAIN);
                    }
                    return Collections.singletonList(MediaType.APPLICATION_JSON);
                })
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
