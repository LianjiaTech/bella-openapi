package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Web Crawl Log Handler
 * Handles usage data population for web crawl requests
 */
@Component
public class WebCrawlLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData endpointProcessData) {
        OpenapiResponse openapiResponse = endpointProcessData.getResponse();
        if (openapiResponse instanceof WebCrawlResponse) {
            WebCrawlResponse response = (WebCrawlResponse) openapiResponse;

            // Create usage object from request and response data
            WebCrawlUsage usage = createUsageFromResponse(response, endpointProcessData);

            // Set usage in process data for billing (no longer set in response)
            endpointProcessData.setUsage(usage);
        }
    }

    /**
     * Create WebCrawlUsage object from response and request data
     */
    private WebCrawlUsage createUsageFromResponse(WebCrawlResponse response, EndpointProcessData processData) {
        // Get pages mapped from response results
        int pagesMapped = response.getResults() != null ? response.getResults().size() : 0;

        // Check if instructions were provided in the request
        boolean hasInstructions = hasInstructionsInRequest(processData);

        // For now, assume successful extractions equal pages mapped
        // In a real implementation, you might need more sophisticated logic
        int successfulExtractions = pagesMapped;

        // Get extract depth from request
        String extractDepth = getExtractDepthFromRequest(processData);

        WebCrawlUsage usage = new WebCrawlUsage();
        usage.setPagesMapped(pagesMapped);
        usage.setHasInstructions(hasInstructions);
        usage.setSuccessfulExtractions(successfulExtractions);
        usage.setExtractDepth(extractDepth);
        return usage;
    }

    /**
     * Check if natural language instructions were provided in the request
     */
    private boolean hasInstructionsInRequest(EndpointProcessData processData) {
        Object request = processData.getRequest();
        if (request instanceof WebCrawlRequest) {
            WebCrawlRequest crawlRequest = (WebCrawlRequest) request;
            return StringUtils.isNotBlank(crawlRequest.getInstructions());
        }
        return false;
    }

    /**
     * Extract extraction depth from request parameters
     */
    private String getExtractDepthFromRequest(EndpointProcessData processData) {
        Object request = processData.getRequest();
        if (request instanceof WebCrawlRequest) {
            WebCrawlRequest crawlRequest = (WebCrawlRequest) request;
            if (crawlRequest.getExtractDepth() != null) {
                return crawlRequest.getExtractDepth().getValue();
            }
        }

        // Default to basic if not specified
        return "basic";
    }

    @Override
    public String endpoint() {
        return "/v1/web/crawl";
    }
}
