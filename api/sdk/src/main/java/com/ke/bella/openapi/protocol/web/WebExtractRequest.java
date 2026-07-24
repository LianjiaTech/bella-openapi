package com.ke.bella.openapi.protocol.web;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Extract Request based on Tavily Extract API
 * Extract web page content from one or more specified URLs using Tavily Extract
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class WebExtractRequest implements UserRequest, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;

    /**
     * The URLs to extract content from (required)
     * Example: ["https://en.wikipedia.org/wiki/Artificial_intelligence"]
     */
    @NotEmpty(message = "URLs cannot be empty")
    private List<String> urls;

    /**
     * Model to use for the extract request
     */
    private String model;

    /**
     * A unique identifier representing your end-user
     */
    private String user;

    /**
     * Include a list of images extracted from the URLs in the response
     * Default: false
     */
    @Nullable
    @JsonProperty("include_images")
    private Boolean includeImages = false;

    /**
     * Whether to include the favicon URL for each result
     * Default: false
     */
    @Nullable
    @JsonProperty("include_favicon")
    private Boolean includeFavicon = false;

    /**
     * The depth of the extraction process
     * - basic: costs 1 credit per 5 successful URL extractions
     * - advanced: costs 2 credits per 5 successful URL extractions
     * Default: basic
     */
    @Nullable
    @JsonProperty("extract_depth")
    private ExtractDepth extractDepth = ExtractDepth.BASIC;

    /**
     * The format of the extracted web page content
     * - markdown: returns content in markdown format
     * - text: returns plain text and may increase latency
     * Default: markdown
     */
    @Nullable
    private Format format = Format.MARKDOWN;

    /**
     * Maximum time in seconds to wait for the URL extraction before timing out
     * Must be between 1.0 and 60.0 seconds
     * If not specified, default timeouts are applied based on extract_depth:
     * - 10 seconds for basic extraction
     * - 30 seconds for advanced extraction
     */
    @Nullable
    @DecimalMin(value = "1.0", message = "timeout must be at least 1.0 seconds")
    @DecimalMax(value = "60.0", message = "timeout must be at most 60.0 seconds")
    private Double timeout;

    /**
     * Extra body fields for future extensibility
     */
    @JsonIgnore
    private Map<String, Object> extra_body;

    // Memory cleanup related fields and methods
    @JsonIgnore
    private volatile boolean cleared = false;

    /**
     * Extract depth enum for extraction complexity
     */
    @AllArgsConstructor
    public enum ExtractDepth {
        BASIC("basic"),
        ADVANCED("advanced");

        private final String value;

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * Format enum for content extraction format
     */
    @AllArgsConstructor
    public enum Format {
        MARKDOWN("markdown"),
        TEXT("text");

        private final String value;

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * Flatten extra_body fields to the outer JSON during serialization
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        return extra_body == null || extra_body.isEmpty() ? null : extra_body;
    }

    /**
     * Handle unknown properties during deserialization and store them in
     * extra_body
     */
    @JsonAnySetter
    public void setExtraBodyField(String key, Object value) {
        if(extra_body == null) {
            extra_body = new HashMap<>();
        }
        extra_body.put(key, value);
    }

    @Override
    public void clearLargeData() {
        if(!cleared) {
            // Clear fields that consume the most memory
            this.urls = null;
            this.extra_body = null;

            // Mark as cleared
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }
}
