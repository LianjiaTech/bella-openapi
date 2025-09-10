package com.ke.bella.openapi.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ke.bella.openapi.utils.HttpUtils;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.service.OpenAiService;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

import java.util.concurrent.ExecutorService;

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
        ObjectMapper mapper = OpenAiService.defaultObjectMapper();
        
        // 使用HttpUtils的defaultOkhttpClient，它会自动包含当前线程的BellaInterceptor
        OkHttpClient client = HttpUtils.defaultOkhttpClient();
        
        Retrofit retrofit = OpenAiService.defaultRetrofit(client, mapper, openapiProperties.getHost() + "/v1/");
        OpenAiApi openAiApi = retrofit.create(OpenAiApi.class);
        ExecutorService executorService = client.dispatcher().executorService();
        
        return new OpenAiService(openAiApi, executorService);
    }

    /**
     * 创建一个使用当前BellaContext的OpenAiService实例（带自定义超时）
     */
    public OpenAiService create(int connectTimeoutSeconds, int readTimeoutSeconds) {
        ObjectMapper mapper = OpenAiService.defaultObjectMapper();
        
        // 创建带有自定义超时的client
        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new com.ke.bella.openapi.request.BellaInterceptor(
                    com.ke.bella.openapi.BellaContext.snapshot()))
                .connectTimeout(connectTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(readTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        
        Retrofit retrofit = OpenAiService.defaultRetrofit(client, mapper, openapiProperties.getHost() + "/v1/");
        OpenAiApi openAiApi = retrofit.create(OpenAiApi.class);
        ExecutorService executorService = client.dispatcher().executorService();
        
        return new OpenAiService(openAiApi, executorService);
    }
}
