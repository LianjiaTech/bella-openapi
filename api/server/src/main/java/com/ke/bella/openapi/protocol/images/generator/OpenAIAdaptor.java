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
        Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());

        requestBuilder.url(url)
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
                        JacksonUtils.serialize(request)));

        Request httpRequest = requestBuilder.build();
        return HttpUtils.httpRequest(httpRequest, ImagesResponse.class);
    }
}
