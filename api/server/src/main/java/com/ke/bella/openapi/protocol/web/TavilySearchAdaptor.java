package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

/**
 * Tavily Web Search Adaptor Implementation for Tavily Search API
 */
@Component("TavilyWebSearchAdaptor")
public class TavilySearchAdaptor implements WebSearchAdaptor<WebSearchProperty> {

    @Override
    public String endpoint() {
        return "/v1/web/search";
    }

    @Override
    public String getDescription() {
        return "Tavily网络搜索协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return WebSearchProperty.class;
    }

    @Override
    public WebSearchResponse search(WebSearchRequest request, String url, WebSearchProperty property) {

        // Build HTTP request
        Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
        requestBuilder.url(url)
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
                        JacksonUtils.toByte(request)));

        Request httpRequest = requestBuilder.build();

        // Clear large data to free memory
        clearLargeData(request);

        // Execute HTTP request and return response
        return HttpUtils.httpRequest(httpRequest, WebSearchResponse.class);
    }
}
