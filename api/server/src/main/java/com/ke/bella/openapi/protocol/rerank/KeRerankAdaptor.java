package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component("KeRerankAdaptor")
public class KeRerankAdaptor implements RerankAdaptor<RerankProperty> {
    @Override
    public RerankResponse rerank(RerankRequest request, String url, RerankProperty property) {
        Request httpRequest = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(requestConvert(request))))
                .build();
        clearLargeData(request);
        return HttpUtils.httpRequest(httpRequest, RerankResponse.class, errorCallback);
    }

    private final Callbacks.ChannelErrorCallback<RerankResponse> errorCallback = (response, httpResponse) -> response.setError(
            OpenapiResponse.OpenapiError.builder()
                    .code(response.getCode())
                    .type(response.getCode())
                    .message(response.getMessage())
                    .httpCode(httpResponse.code())
                    .build());

    KeRerankRequest requestConvert(RerankRequest request) {
        KeRerankRequest keRequest = new KeRerankRequest();
        keRequest.setQuery(request.getQuery());
        keRequest.setDocuments(request.getDocuments());
        keRequest.setIntroduction(request.getInstruct());
        keRequest.setTopN(request.getTopN());
        return keRequest;
    }

    @Override
    public String getDescription() {
        return "KE Rerank协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return RerankProperty.class;
    }
}
