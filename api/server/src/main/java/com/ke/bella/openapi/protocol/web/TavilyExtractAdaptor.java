package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

/**
 * Tavily Web Extract Adaptor
 * Implementation for Tavily Extract API
 */
@Component("TavilyWebExtractAdaptor")
public class TavilyExtractAdaptor implements WebExtractAdaptor<WebExtractProperty> {

    @Override
    public String endpoint() {
        return "/v1/web/extract";
    }

    @Override
    public String getDescription() {
        return "Tavily网络内容提取协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return WebExtractProperty.class;
    }

    @Override
    public WebExtractResponse extract(WebExtractRequest request, String url, WebExtractProperty property) {

        // Build HTTP request
        Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
        requestBuilder.url(url)
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
                        JacksonUtils.toByte(request)));

        Request httpRequest = requestBuilder.build();

        // Clear large data to free memory
        clearLargeData(request);

        // Execute HTTP request and return response
        return HttpUtils.httpRequest(httpRequest, WebExtractResponse.class);
    }
}
