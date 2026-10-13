package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.IProtocolProperty;

/**
 * Web Extract Adaptor Interface
 * Defines the contract for web content extraction adaptors
 *
 * @param <T> The property type that extends IProtocolProperty
 */
public interface WebExtractAdaptor<T extends IProtocolProperty> extends IProtocolAdaptor {

    /**
     * Extract content from specified URLs
     *
     * @param request  The web extract request containing URLs and configuration
     * @param url      The endpoint URL to send the request to
     * @param property The protocol-specific properties (authentication, etc.)
     * 
     * @return WebExtractResponse containing extracted content and metadata
     */
    WebExtractResponse extract(WebExtractRequest request, String url, T property);
}
