package com.ke.bella.openapi.protocol.images.generator;

import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * OpenAI文生图适配器
 */
@Component("OpenAIImagesGenerator")
public class OpenAIAdaptor implements ImagesGeneratorAdaptor<ImagesProperty> {
    
    @Override
    public String endpoint() {
        return "/v1/images/generations";
    }
    
    @Override
    public String getDescription() {
        return "OpenAI文生图协议";
    }
    
    @Override
    public Class<?> getPropertyClass() {
        return ImagesProperty.class;
    }
    
    @Override
    public ImagesResponse generateImages(ImagesRequest request, String url, ImagesProperty property) {
        request.setModel(property.getDeployName());
        Object requestBody = buildRequestBody(request);
        Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());

        requestBuilder.url(url)
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
                        JacksonUtils.serialize(requestBody)));

        Request httpRequest = requestBuilder.build();
        return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
    }

    /**
     * 构建请求体，extra_body 中的所有参数都释放出来，存在就覆盖
     */
    private Object buildRequestBody(ImagesRequest request) {
        Map<String, Object> extraBody = request.getExtra_body();

        if (extraBody == null || extraBody.isEmpty()) {
            return request;
        }

        // 将 request 对象转换为 Map
        @SuppressWarnings("unchecked")
        Map<String, Object> requestMap = JacksonUtils.toMap(request);

        if (requestMap != null) {
            requestMap.remove("extra_body");

            requestMap.putAll(extraBody);
        }
        
        return requestMap;
    }

}
