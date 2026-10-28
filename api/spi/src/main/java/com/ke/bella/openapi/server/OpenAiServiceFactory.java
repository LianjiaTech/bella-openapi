package com.ke.bella.openapi.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.request.BellaInterceptor;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;

import java.util.Map;
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
        Map<String, Object> context = BellaContext.snapshot();

        // 创建带有自定义超时的client
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new BellaInterceptor(openapiProperties.getHost(), context))
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
     * 创建一个使用指定 API key 的 OpenAiService 实例（带自定义超时）
     */
    public OpenAiService create(String apikey, int connectTimeoutSeconds, int readTimeoutSeconds) {
        ObjectMapper mapper = OpenAiService.defaultObjectMapper();
        Map<String, Object> context = BellaContext.snapshot();

        // 创建带有自定义超时的client
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new BellaInterceptor(openapiProperties.getHost(), context))
                .addInterceptor(chain -> {
                    Request originalRequest = chain.request();
                    Request.Builder bellaRequest = originalRequest.newBuilder();
                    bellaRequest.removeHeader(BellaContext.SERVICE_CLIENT_HEADER);
                    bellaRequest.removeHeader(BellaContext.EFFECTIVE_API_KEY_CODE_HEADER);
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
