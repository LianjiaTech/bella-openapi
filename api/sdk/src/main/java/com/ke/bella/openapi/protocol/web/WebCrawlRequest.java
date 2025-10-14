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
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Crawl Request based on Tavily Crawl API Provides comprehensive web crawling functionality with configurable depth and filtering options
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class WebCrawlRequest implements UserRequest, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;

    /**
     * The root URL to begin the crawl (required) Example: "docs.tavily.com"
     */
    @NotBlank(message = "URL cannot be blank")
    private String url;

    /**
     * Model to use for the crawl request
     */
    private String model;

    /**
     * A unique identifier representing your end-user
     */
    private String user;

    /**
     * Natural language instructions for the crawler When specified, the mapping cost increases to 2 API credits per 10 successful pages instead of 1
     * API credit per 10 pages Example: "Find all pages about the Python SDK"
     */
    @Nullable
    private String instructions;

    /**
     * Max depth of the crawl. Defines how far from the base URL the crawler can explore Default: 1
     */
    @Nullable
    @Min(value = 1, message = "max_depth must be at least 1")
    @JsonProperty("max_depth")
    private Integer maxDepth = 1;

    /**
     * Max number of links to follow per level of the tree (i.e., per page) Default: 20
     */
    @Nullable
    @Min(value = 1, message = "max_breadth must be at least 1")
    @JsonProperty("max_breadth")
    private Integer maxBreadth = 20;

    /**
     * Total number of links the crawler will process before stopping Default: 50
     */
    @Nullable
    @Min(value = 1, message = "limit must be at least 1")
    private Integer limit = 50;

    /**
     * Regex patterns to select only URLs with specific path patterns Example: ["/docs/.*", "/api/v1.*"]
     */
    @Nullable
    @JsonProperty("select_paths")
    private List<String> selectPaths;

    /**
     * Regex patterns to select crawling to specific domains or subdomains Example: ["^docs\\.example\\.com$"]
     */
    @Nullable
    @JsonProperty("select_domains")
    private List<String> selectDomains;

    /**
     * Regex patterns to exclude URLs with specific path patterns Example: ["/private/.*", "/admin/.*"]
     */
    @Nullable
    @JsonProperty("exclude_paths")
    private List<String> excludePaths;

    /**
     * Regex patterns to exclude specific domains or subdomains from crawling Example: ["^private\\.example\\.com$"]
     */
    @Nullable
    @JsonProperty("exclude_domains")
    private List<String> excludeDomains;

    /**
     * Whether to include external domain links in the final results list Default: true
     */
    @Nullable
    @JsonProperty("allow_external")
    private Boolean allowExternal = true;

    /**
     * Whether to include images in the crawl results Default: false
     */
    @Nullable
    @JsonProperty("include_images")
    private Boolean includeImages = false;

    /**
     * Advanced extraction retrieves more data, including tables and embedded content, with higher success but may increase latency - basic: costs 1
     * credit per 5 successful extractions - advanced: costs 2 credits per 5 successful extractions Default: basic
     */
    @Nullable
    @JsonProperty("extract_depth")
    private ExtractDepth extractDepth = ExtractDepth.BASIC;

    /**
     * The format of the extracted web page content - markdown: returns content in markdown format - text: returns plain text and may increase latency
     * Default: markdown
     */
    @Nullable
    private Format format = Format.MARKDOWN;

    /**
     * Whether to include the favicon URL for each result Default: false
     */
    @Nullable
    @JsonProperty("include_favicon")
    private Boolean includeFavicon = false;

    /**
     * Extra body fields for future extensibility
     */
    @JsonIgnore
    private Map<String, Object> extra_body;
    // Memory cleanup related fields and methods
    @JsonIgnore
    private volatile boolean cleared = false;

    /**
     * Flatten extra_body fields to the outer JSON during serialization
     */
    @JsonAnyGetter
    public Map<String, Object> getExtraBodyFields() {
        return extra_body == null || extra_body.isEmpty() ? null : extra_body;
    }

    /**
     * Handle unknown properties during deserialization and store them in extra_body
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
            this.selectPaths = null;
            this.selectDomains = null;
            this.excludePaths = null;
            this.excludeDomains = null;
            this.extra_body = null;

            // Mark as cleared
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }

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
}
