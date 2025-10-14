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
 * Web Extract Response based on Tavily Extract API
 * Contains extracted content, metadata, and timing information from web extraction
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class WebExtractResponse extends OpenapiResponse {
    private static final long serialVersionUID = 1L;

    /**
     * A list of extracted content from the provided URLs
     */
    private List<ExtractResult> results;

    /**
     * A list of URLs that could not be processed
     */
    @Nullable
    @JsonProperty("failed_results")
    private List<FailedResult> failedResults;

    /**
     * Time in seconds it took to complete the request
     * Example: 0.02
     */
    @JsonProperty("response_time")
    private Double responseTime;

    /**
     * A unique request identifier you can share with customer support
     * to help resolve issues with specific requests
     * Example: "123e4567-e89b-12d3-a456-426614174111"
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
     * Extract Result class representing individual extraction results
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class ExtractResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * The URL from which the content was extracted
         * Example: "https://en.wikipedia.org/wiki/Artificial_intelligence"
         */
        private String url;

        /**
         * The full content extracted from the page
         * This contains the complete extracted content in the specified format (markdown or text)
         */
        @JsonProperty("raw_content")
        private String rawContent;

        /**
         * A list of image URLs extracted from the page
         * Only available if include_images is set to true
         */
        @Nullable
        private List<String> images;

        /**
         * The favicon URL for the result
         * Only present if include_favicon was set to true in the request
         * Example: "https://en.wikipedia.org/static/favicon/wikipedia.ico"
         */
        @Nullable
        private String favicon;
    }

    /**
     * Failed Result class representing URLs that could not be processed
     */
    @Data
    @SuperBuilder
    @NoArgsConstructor
    public static class FailedResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * The URL that failed to be processed
         */
        private String url;

        /**
         * The reason why the URL failed to be processed
         */
        private String reason;

        /**
         * HTTP status code if applicable
         */
        @Nullable
        @JsonProperty("status_code")
        private Integer statusCode;
    }

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
        if (extraFields == null) {
            extraFields = new HashMap<>();
        }
        extraFields.put(key, value);
    }
}