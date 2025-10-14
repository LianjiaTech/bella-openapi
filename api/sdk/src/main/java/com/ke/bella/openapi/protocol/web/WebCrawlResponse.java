package com.ke.bella.openapi.protocol.web;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Crawl Response based on Tavily Crawl API Contains crawled content, metadata, and timing information from web crawling
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class WebCrawlResponse extends OpenapiResponse {
    private static final long serialVersionUID = 1L;

    /**
     * The base URL that was crawled Example: "docs.tavily.com"
     */
    @JsonProperty("base_url")
    private String baseUrl;

    /**
     * A list of extracted content from the crawled URLs
     */
    private List<CrawlResult> results;

    /**
     * Time in seconds it took to complete the request Example: 1.23
     */
    @JsonProperty("response_time")
    private Double responseTime;

    /**
     * A unique request identifier you can share with customer support to help resolve issues with specific requests Example:
     * "123e4567-e89b-12d3-a456-426614174111"
     */
    @Nullable
    @JsonProperty("request_id")
    private String requestId;

    /**
     * Extra fields for future extensibility
     */
    @JsonIgnore
    private Map<String, Object> extraFields;

    /**
     * Flatten extra fields to the outer JSON during serialization
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraFields() {
        return extraFields == null || extraFields.isEmpty() ? null : extraFields;
    }

    /**
     * Handle unknown properties during deserialization and store them in extraFields
     */
    @JsonAnySetter
    public void setExtraField(String key, Object value) {
        if(extraFields == null) {
            extraFields = new HashMap<>();
        }
        extraFields.put(key, value);
    }

    /**
     * Crawl Result class representing individual crawled page results
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class CrawlResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * The URL that was crawled Example: "https://docs.tavily.com"
         */
        private String url;

        /**
         * The full content extracted from the page This contains the complete extracted content in the specified format (markdown or text)
         */
        @JsonProperty("raw_content")
        private String rawContent;

        /**
         * The favicon URL for the result Only present if include_favicon was set to true in the request Example:
         * "https://mintlify.s3-us-west-1.amazonaws.com/tavilyai/_generated/favicon/apple-touch-icon.png?v=3"
         */
        @Nullable
        private String favicon;
    }
}
