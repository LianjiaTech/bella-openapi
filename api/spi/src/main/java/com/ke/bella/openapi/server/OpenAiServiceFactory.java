package com.ke.bella.openapi.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.request.BellaInterceptor;
import com.ke.bella.openapi.utils.HttpUtils;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.collections4.MapUtils;
import retrofit2.Retrofit;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * OpenAiService工厂，每次调用都创建新的实例并使用当前BellaContext
 */
public class OpenAiServiceFactory {

    private final OpenapiProperties openapiProperties;

    public OpenAiServiceFactory(OpenapiProperties openapiProperties) {
        this.openapiProperties = openapiProperties;
    }

    /**
     * 创建一个使用当前BellaContext的OpenAiService实例
     * 每次调用都会获取当前线程的BellaContext并创建新的OpenAiService
     */
    public OpenAiService create() {
        return create(10, 120);
    }

    /**
     * 创建一个使用当前BellaContext的OpenAiService实例（带自定义超时）
     */
    public OpenAiService create(int connectTimeoutSeconds, int readTimeoutSeconds) {
        ObjectMapper mapper = OpenAiService.defaultObjectMapper();

        // 创建带有自定义超时的client
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new BellaInterceptor(openapiProperties.getHost(), BellaContext.snapshot()))
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = OpenAiService.defaultRetrofit(client, mapper, openapiProperties.getHost() + "/v1/");
        OpenAiApi openAiApi = retrofit.create(OpenAiApi.class);
        ExecutorService executorService = client.dispatcher().executorService();

        return new OpenAiService(openAiApi, executorService);
    }

    public OpenAiService create(String apikey) {
        return create(apikey, 10, 120);
    }

    /**
     * 创建一个使用当前BellaContext的OpenAiService实例（带自定义超时）, 使用d
     */
    public OpenAiService create(String apikey, int connectTimeoutSeconds, int readTimeoutSeconds) {
        ObjectMapper mapper = OpenAiService.defaultObjectMapper();

        // 创建带有自定义超时的client
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new BellaInterceptor(openapiProperties.getHost(), BellaContext.snapshot()))
                .addInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    Request.Builder bellaRequest = originalRequest.newBuilder();
                    bellaRequest.header("Authorization", "Bearer " + apikey);
                    return chain.proceed(bellaRequest.build());
                })
                .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = OpenAiService.defaultRetrofit(client, mapper, openapiProperties.getHost() + "/v1/");
        OpenAiApi openAiApi = retrofit.create(OpenAiApi.class);
        ExecutorService executorService = client.dispatcher().executorService();

        return new OpenAiService(openAiApi, executorService);
    }
}
