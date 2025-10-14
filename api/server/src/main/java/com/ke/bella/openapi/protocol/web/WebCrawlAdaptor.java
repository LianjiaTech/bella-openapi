package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;

/**
 * Web Crawl Adaptor Interface Adaptor interface for web crawl functionality using Tavily Crawl API
 */
public interface WebCrawlAdaptor<T extends WebCrawlProperty> extends IProtocolAdaptor {

    /**
     * Perform web crawl
     *
     * @param request  web crawl request parameters
     * @param url      request URL
     * @param property protocol configuration properties
     *
     * @return web crawl response
     */
    WebCrawlResponse crawl(WebCrawlRequest request, String url, T property);
}
