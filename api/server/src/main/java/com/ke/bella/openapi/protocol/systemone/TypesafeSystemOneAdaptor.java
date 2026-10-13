package com.ke.bella.openapi.protocol.systemone;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component("SystemOneAdaptor")
public class TypesafeSystemOneAdaptor implements SystemOneAdaptor<SystemOneProperty> {
    @Override
    public SystemOneResponse evaluate(SystemOneRequest request, String url, SystemOneProperty property) {
        Request httpRequest = buildRequest(request, url, property);
        clearLargeData(request);
        return HttpUtils.httpRequest(httpRequest, SystemOneResponse.class, errorCallback);
    }

    Request buildRequest(SystemOneRequest request, String url, SystemOneProperty property) {
        return authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)))
                .build();
    }

    final Callbacks.ChannelErrorCallback<SystemOneResponse> errorCallback = (response, httpResponse) -> response.setError(
            OpenapiResponse.OpenapiError.builder()
                    .code(String.valueOf(httpResponse.code()))
                    .type(String.valueOf(httpResponse.code()))
                    .message(response.getExtra().get("message") == null ? httpResponse.message() : String.valueOf(response.getExtra().get("message")))
                    .httpCode(httpResponse.code())
                    .build());

    @Override
    public String getDescription() {
        return "TypeSafe System One协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return SystemOneProperty.class;
    }
}
