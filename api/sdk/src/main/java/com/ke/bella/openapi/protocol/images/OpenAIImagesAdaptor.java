package com.ke.bella.openapi.protocol.images;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI文生图适配器
 */
@Slf4j
public class OpenAIImagesAdaptor implements ImagesAdaptor<ImagesProperty> {
    
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    @Override
    public String endpoint() {
        return "/v1/images/generations";
    }
    
    @Override
    public String getDescription() {
        return "OpenAI文生图适配器";
    }
    
    @Override
    public Class<?> getPropertyClass() {
        return ImagesProperty.class;
    }
    
    @Override
    public ImagesResponse generateImages(ImagesRequest request, String url, ImagesProperty property) {
        try {
            // 构建请求
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(
                            JacksonUtils.serialize(preprocessRequest(request, property)),
                            MediaType.get("application/json; charset=utf-8")));
            
            // 添加授权头
            AuthorizationProperty authProperty = new AuthorizationProperty();
            authProperty.setType(AuthorizationProperty.Type.BEARER);
            authProperty.setApiKey(getApiKeyFromProperty(property));
            requestBuilder = authorizationRequestBuilder(authProperty);
            requestBuilder.url(url)
                    .post(RequestBody.create(
                            JacksonUtils.serialize(preprocessRequest(request, property)),
                            MediaType.get("application/json; charset=utf-8")));
            
            // 添加额外请求头
            if (property.getExtraHeaders() != null) {
                property.getExtraHeaders().forEach(requestBuilder::header);
            }
            
            Request httpRequest = requestBuilder.build();
            
            // 发送请求
            try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Request failed: " + response.code() + " " + response.message());
                }
                
                String responseBody = response.body().string();
                return JacksonUtils.deserialize(responseBody, new TypeReference<ImagesResponse>() {});
            }
            
        } catch (IOException e) {
            log.error("Failed to generate images", e);
            throw new RuntimeException("Failed to generate images: " + e.getMessage(), e);
        }
    }
    
    /**
     * 预处理请求参数
     */
    private ImagesRequest preprocessRequest(ImagesRequest request, ImagesProperty property) {
        // 设置默认值
        if (StringUtils.isBlank(request.getResponse_format())) {
            request.setResponse_format(property.getDefaultResponseFormat());
        }
        if (StringUtils.isBlank(request.getSize())) {
            request.setSize(property.getDefaultSize());
        }
        if (request.getN() == null) {
            request.setN(1);
        }
        
        return request;
    }
    
    /**
     * 从属性中获取API密钥
     */
    private String getApiKeyFromProperty(ImagesProperty property) {
        // 这里需要根据实际情况从property中获取API密钥
        return ""; // 实际实现中需要从配置中获取
    }
}
