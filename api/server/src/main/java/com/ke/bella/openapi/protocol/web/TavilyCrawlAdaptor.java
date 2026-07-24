package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

/**
 * Tavily Web Crawl Adaptor Implementation for Tavily Crawl API
 */
@Component("TavilyWebCrawlAdaptor")
public class TavilyCrawlAdaptor implements WebCrawlAdaptor<WebCrawlProperty> {

    @Override
    public String endpoint() {
        return "/v1/web/crawl";
    }

    @Override
    public String getDescription() {
        return "Tavily网络爬虫协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return WebCrawlProperty.class;
    }

    @Override
    public WebCrawlResponse crawl(WebCrawlRequest request, String url, WebCrawlProperty property) {

        // Build HTTP request
        Request.Builder requestBuilder = authorizationRequestBuilder(property.getAuth());
        requestBuilder.url(url)
                .post(RequestBody.create(MediaType.get("application/json; charset=utf-8"),
                        JacksonUtils.toByte(request)));

        Request httpRequest = requestBuilder.build();

        // Clear large data to free memory
        clearLargeData(request);

        // Execute HTTP request and return response
        return HttpUtils.httpRequest(httpRequest, WebCrawlResponse.class);
    }
}
