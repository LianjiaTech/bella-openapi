package com.ke.bella.openapi.protocol.web;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.ke.bella.openapi.protocol.IMemoryClearable;
import com.ke.bella.openapi.protocol.UserRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Search Request based on Tavily Search API Provides unified access to web
 * search functionality with comprehensive configuration options
 */
@Data
@SuperBuilder
@NoArgsConstructor
public class WebSearchRequest implements UserRequest, Serializable, IMemoryClearable {
    private static final long serialVersionUID = 1L;

    /**
     * The search query to execute with Tavily (required) Example: "who is Leo
     * Messi?"
     */
    @NotBlank(message = "Query cannot be blank")
    private String query;

    /**
     * Model to use for the search request
     */
    private String model;

    /**
     * A unique identifier representing your end-user
     */
    private String user;

    /**
     * When auto_parameters is enabled, Tavily automatically configures search
     * parameters based on your query's content and intent. Default: false
     */
    @Nullable
    private Boolean auto_parameters = false;

    /**
     * The category of the search. Default: general - news: useful for
     * retrieving real-time updates - general: for broader, more general-purpose
     * searches - finance: for financial information
     */
    @Nullable
    private Topic topic = Topic.GENERAL;

    /**
     * The depth of the search. Default: basic - basic: provides generic content
     * snippets (1 API Credit) - advanced: retrieves most relevant sources
     * and content snippets (2 API Credits)
     */
    private SearchDepth search_depth = SearchDepth.BASIC;

    /**
     * Chunks are short content snippets (maximum 500 characters each) pulled
     * directly from the source. Maximum number of relevant chunks returned per
     * source. Default: 3 Available only when search_depth is advanced.
     */
    @Nullable
    private Integer chunks_per_source;

    /**
     * The maximum number of search results to return. Default: 5
     */
    @Nullable
    private Integer max_results;

    /**
     * The time range back from the current date to filter results (publish
     * date) Useful when looking for sources that have published data
     */
    @Nullable
    private TimeRange time_range;

    /**
     * Number of days back from the current date to include (publish date)
     * Available only if topic is news. Default: 7
     */
    @Nullable
    private Integer days;

    /**
     * Will return all results after the specified start date (publish date)
     * Required to be written in the format YYYY-MM-DD Example: "2025-02-09"
     */
    @Nullable
    private String start_date;

    /**
     * Will return all results before the specified end date (publish date)
     * Required to be written in the format YYYY-MM-DD Example: "2000-01-28"
     */
    @Nullable
    private String end_date;

    /**
     * Include the cleaned and parsed HTML content of each search result.
     * Default: false - markdown or true: returns search result content in
     * markdown
     * format - text: returns the plain text from the results and may increase
     * latency
     */
    @Nullable
    private Boolean include_raw_content = false;

    /**
     * Also perform an image search and include the results in the response.
     * Default: false
     */
    @Nullable
    private Boolean include_images = false;

    /**
     * When include_images is true, also add a descriptive text for each image.
     * Default: false
     */
    @Nullable
    private Boolean include_image_descriptions = false;

    /**
     * Whether to include the favicon URL for each result. Default: false
     */
    @Nullable
    private Boolean include_favicon = false;

    /**
     * A list of domains to specifically include in the search results Maximum
     * 300 domains
     */
    @Nullable
    private List<String> include_domains;

    /**
     * A list of domains to specifically exclude from the search results Maximum
     * 150 domains
     */
    @Nullable
    private List<String> exclude_domains;

    /**
     * Boost search results from a specific country. This will prioritize
     * content from the selected country in the search results. Available only
     * if
     * topic is general.
     */
    @Nullable
    private String country;

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
            this.include_domains = null;
            this.exclude_domains = null;
            this.extra_body = null;
            this.query = null;

            // Mark as cleared
            this.cleared = true;
        }
    }

    @Override
    public boolean isCleared() {
        return cleared;
    }

    /**
     * Topic enum for search category
     */
    @Getter
    @AllArgsConstructor
    public enum Topic {
        GENERAL("general"),
        NEWS("news"),
        FINANCE("finance");

        private final String value;

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * Search depth enum
     */
    @Getter
    @AllArgsConstructor
    public enum SearchDepth {
        BASIC("basic"),
        ADVANCED("advanced");

        private final String value;

        @JsonValue
        public String getValue() {
            return value;
        }
    }

    /**
     * Time range enum for filtering results by publish date
     */
    @Getter
    @AllArgsConstructor
    public enum TimeRange {
        DAY("day"),
        WEEK("week"),
        MONTH("month"),
        YEAR("year"),
        D("d"),
        W("w"),
        M("m"),
        Y("y");

        private final String value;

        @JsonValue
        public String getValue() {
            return value;
        }
    }
}
