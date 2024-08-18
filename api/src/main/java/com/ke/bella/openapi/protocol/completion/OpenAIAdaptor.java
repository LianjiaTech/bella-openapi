package com.ke.bella.openapi.protocol.completion;

import com.alibaba.fastjson.JSON;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocalProperty;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component
public class OpenAIAdaptor implements IProtocolAdaptor.CompletionAdaptor {

    private CompletionSseListener.SseConverter sseConverter = str -> JacksonUtils.deserialize(str, StreamCompletionResponse.class);

    @Override
    public CompletionResponse httpRequest(CompletionRequest request, String url, IProtocalProperty property) {
        Request httpRequest = buildRequest(request, url, (OpenAIProperty) property);
        return HttpUtils.httpRequest(httpRequest, CompletionResponse.class);
    }

    @Override
    public void streamRequest(CompletionRequest request, String url, IProtocalProperty property, Callback.CompletionSseCallback callback) {
        Request httpRequest = buildRequest(request, url, (OpenAIProperty) property);
        HttpUtils.streamRequest(httpRequest, new CompletionSseListener(callback, sseConverter));
    }

    private Request buildRequest(CompletionRequest request, String url, OpenAIProperty property) {
        if(property.getApiVersion() != null) {
            url += property.getApiVersion();
        }
        request.setModel(property.getDeployName());
        Request.Builder builder = authorizationRequestBuilder(property.auth.getType(), property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"),
                        JSON.toJSONString(request)));
        return builder.build();
    }

    @Override
    public Class<OpenAIProperty> getPropertyClass() {
        return OpenAIProperty.class;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenAIProperty implements IProtocalProperty {
        AuthorizationProperty auth;
        String deployName;
        String apiVersion;
    }
}
