package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component("AliRerankAdaptor")
public class AliRerankAdaptor implements RerankAdaptor<RerankProperty> {
    @Override
    public RerankResponse rerank(RerankRequest request, String url, RerankProperty property) {
        if(StringUtils.isNotBlank(property.getDeployName())) {
            request.setModel(property.getDeployName());
        }
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)))
                .build();
        clearLargeData(request);
        return HttpUtils.httpRequest(httpRequest, RerankResponse.class, errorCallback);
    }

    private final Callbacks.ChannelErrorCallback<RerankResponse> errorCallback = (response, httpResponse) -> response.setError(OpenapiResponse.OpenapiError.builder()
            .code(response.getCode())
            .type(response.getCode())
            .message(response.getMessage())
            .httpCode(httpResponse.code())
            .build());

    @Override
    public String getDescription() {
        return "阿里百炼Rerank协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return RerankProperty.class;
    }
}
