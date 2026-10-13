package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import org.springframework.stereotype.Component;

/**
 * Web Extract Log Handler
 * Handles usage data population for web extract requests
 */
@Component
public class WebExtractLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData endpointProcessData) {
        OpenapiResponse openapiResponse = endpointProcessData.getResponse();
        if(openapiResponse instanceof WebExtractResponse) {
            WebExtractResponse response = (WebExtractResponse) openapiResponse;

            // Create usage object from request and response data
            WebExtractUsage usage = createUsageFromResponse(response, endpointProcessData);

            // Set usage in process data for billing (no longer set in response)
            endpointProcessData.setUsage(usage);
        }
    }

    /**
     * Create WebExtractUsage object from response and request data
     */
    private WebExtractUsage createUsageFromResponse(WebExtractResponse response, EndpointProcessData processData) {
        // Get successful extractions from response results
        int successfulExtractions = response.getResults() != null ? response.getResults().size() : 0;

        // Get failed extractions from failed results
        int failedExtractions = response.getFailedResults() != null ? response.getFailedResults().size() : 0;

        // Get extract depth from request
        String extractDepth = getExtractDepthFromRequest(processData);

        // Check if images and favicons were requested
        boolean includeImages = getIncludeImagesFromRequest(processData);
        boolean includeFavicon = getIncludeFaviconFromRequest(processData);

        WebExtractUsage usage = new WebExtractUsage();
        usage.setSuccessfulExtractions(successfulExtractions);
        usage.setFailedExtractions(failedExtractions);
        usage.setExtractDepth(extractDepth);
        usage.setIncludeImages(includeImages);
        usage.setIncludeFavicon(includeFavicon);
        return usage;
    }

    /**
     * Extract extraction depth from request parameters
     */
    private String getExtractDepthFromRequest(EndpointProcessData processData) {
        Object request = processData.getRequest();
        if(request instanceof WebExtractRequest) {
            WebExtractRequest extractRequest = (WebExtractRequest) request;
            if(extractRequest.getExtractDepth() != null) {
                return extractRequest.getExtractDepth().getValue();
            }
        }

        // Default to basic if not specified
        return "basic";
    }

    /**
     * Check if images were requested in the extract request
     */
    private boolean getIncludeImagesFromRequest(EndpointProcessData processData) {
        Object request = processData.getRequest();
        if(request instanceof WebExtractRequest) {
            WebExtractRequest extractRequest = (WebExtractRequest) request;
            return Boolean.TRUE.equals(extractRequest.getIncludeImages());
        }
        return false;
    }

    /**
     * Check if favicons were requested in the extract request
     */
    private boolean getIncludeFaviconFromRequest(EndpointProcessData processData) {
        Object request = processData.getRequest();
        if(request instanceof WebExtractRequest) {
            WebExtractRequest extractRequest = (WebExtractRequest) request;
            return Boolean.TRUE.equals(extractRequest.getIncludeFavicon());
        }
        return false;
    }

    @Override
    public String endpoint() {
        return "/v*/web/extract";
    }
}
