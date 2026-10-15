package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component("DoubaoSearchAdaptor")
public class DoubaoSearchAdaptor implements WebSearchAdaptor<DoubaoSearchRequest, DoubaoSearchProperty> {
    @Override public String endpoint() { return "/v1/web/search"; }
    @Override public String getDescription() { return "豆包搜索 Custom 协议"; }
    @Override public Class<?> getPropertyClass() { return DoubaoSearchProperty.class; }
    @Override public Class<DoubaoSearchRequest> getRequestClass() { return DoubaoSearchRequest.class; }
    @Override public DoubaoSearchResponse search(DoubaoSearchRequest request, String url, DoubaoSearchProperty property) {
        String validation = request.validate();
        if(validation != null) {
            DoubaoSearchResponse response = new DoubaoSearchResponse();
            response.setError(new OpenapiResponse.OpenapiError("invalid_request_error", validation, 400));
            return response;
        }
        Request httpRequest = authorizationRequestBuilder(property == null ? null : property.getAuth()).url(url)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"), JacksonUtils.toByte(request))).build();
        clearLargeData(request);
        return HttpUtils.httpRequest(httpRequest, DoubaoSearchResponse.class);
    }
}
