package com.ke.bella.openapi.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.client.OpenapiClient;
import com.ke.bella.openapi.request.BellaRequestFilter;
import com.ke.bella.openapi.server.intercept.AuthorizationInterceptor;
import com.ke.bella.openapi.server.intercept.ConcurrentStartInterceptor;
import com.ke.bella.openapi.utils.HttpUtils;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import retrofit2.Retrofit;

import java.util.concurrent.ExecutorService;

@EnableConfigurationProperties(OpenapiProperties.class)
@Configuration
public class BellaServiceConfiguration implements WebMvcConfigurer {

    @Autowired
    @Lazy
    private ConcurrentStartInterceptor concurrentStartInterceptor;
    @Autowired
    @Lazy
    private AuthorizationInterceptor authorizationInterceptor;

    public BellaServiceConfiguration() {
        HttpUtils.useBellaInterceptor();
    }


    @Bean
    public OpenapiClient openapiClient(OpenapiProperties properties) {
        return new OpenapiClient(properties.getHost(), properties.getServiceAk());
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

    @Bean
    public OpenAiService openAiService(OpenapiProperties openapiProperties) {
        ObjectMapper mapper = OpenAiService.defaultObjectMapper();

        // 使用HttpUtils的defaultOkhttpClient，它会自动包含BellaInterceptor
        OkHttpClient client = HttpUtils.defaultOkhttpClient();

        Retrofit retrofit = OpenAiService.defaultRetrofit(client, mapper, openapiProperties.getHost() + "/v1/");
        OpenAiApi openAiApi = retrofit.create(OpenAiApi.class);
        ExecutorService executorService = client.dispatcher().executorService();

        return new OpenAiService(openAiApi, executorService);
    }

    @Bean
    public OpenAiServiceFactory openAiServiceFactory(OpenapiProperties openapiProperties) {
        return new OpenAiServiceFactory(openapiProperties);
    }


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/console/**")
                .addPathPatterns("/v*/**")
                .order(100);
        registry.addInterceptor(concurrentStartInterceptor);
    }
}
