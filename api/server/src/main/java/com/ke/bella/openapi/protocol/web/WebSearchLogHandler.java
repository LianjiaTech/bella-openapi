package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import org.springframework.stereotype.Component;

/**
 * Web Search Log Handler
 * Handles usage data population for web search requests
 */
@Component
public class WebSearchLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData endpointProcessData) {
        OpenapiResponse openapiResponse = endpointProcessData.getResponse();
        WebSearchUsage usage = null;
        if(openapiResponse instanceof WebSearchResponse) {
            usage = createUsageFromResponse((WebSearchResponse) openapiResponse, endpointProcessData);
        } else if(openapiResponse instanceof DoubaoSearchResponse) {
            usage = createUsageFromDoubaoResponse((DoubaoSearchResponse) openapiResponse, endpointProcessData);
        }
        if(usage != null) {
            endpointProcessData.setUsage(usage);
        }
    }

    /**
     * Create WebSearchUsage object from response and request data
     */
    private WebSearchUsage createUsageFromResponse(WebSearchResponse response, EndpointProcessData processData) {
        // Get search depth from request or auto_parameters
        String searchDepth = getSearchDepthFromRequest(response, processData);

        // Get result count from response
        int resultCount = response.getResults() != null ? response.getResults().size() : 0;

        // Check if images were included
        boolean includeImages = response.getImages() != null && !response.getImages().isEmpty();

        WebSearchUsage usage = new WebSearchUsage();
        usage.setRequestCount(1);
        usage.setSearchDepth(searchDepth);
        usage.setResultCount(resultCount);
        usage.setIncludeImages(includeImages);
        return usage;
    }

    /**
     * Extract search depth from request parameters or auto_parameters
     */
    private String getSearchDepthFromRequest(WebSearchResponse response, EndpointProcessData processData) {
        // First check auto_parameters in response
        if(response.getAutoParameters() != null && response.getAutoParameters().getSearchDepth() != null) {
            return response.getAutoParameters().getSearchDepth();
        }

        // Fall back to request parameters if available
        Object request = processData.getRequest();
        if(request instanceof WebSearchRequest) {
            WebSearchRequest searchRequest = (WebSearchRequest) request;
            if(searchRequest.getSearch_depth() != null) {
                return searchRequest.getSearch_depth().getValue();
            }
        }

        // Default to basic if not specified
        return "basic";
    }

    private WebSearchUsage createUsageFromDoubaoResponse(DoubaoSearchResponse response, EndpointProcessData processData) {
        WebSearchUsage usage = new WebSearchUsage();
        usage.setRequestCount(1);
        return usage;
    }

    @Override
    public String endpoint() {
        return "/v1/web/search";
    }
}
