package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;

/**
 * Web Search Adaptor Interface Adaptor interface for web search functionality
 * using Tavily Search API
 */
public interface WebSearchAdaptor<R, T extends com.ke.bella.openapi.protocol.IProtocolProperty> extends IProtocolAdaptor {

    Class<R> getRequestClass();

    /**
     * Perform web search
     *
     * @param request  web search request parameters
     * @param url      request URL
     * @param property protocol configuration properties
     *
     * @return web search response
     */
    com.ke.bella.openapi.protocol.OpenapiResponse search(R request, String url, T property);
}
