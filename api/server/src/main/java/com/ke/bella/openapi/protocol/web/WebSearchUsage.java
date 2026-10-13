package com.ke.bella.openapi.protocol.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Web Search Usage class for billing calculation
 * Independent usage class that tracks search-specific metrics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSearchUsage {

    /**
     * The search depth used for this request (basic or advanced)
     * Used to determine billing cost
     */
    private String searchDepth;

    /**
     * Number of results returned
     */
    private int resultCount;

    /**
     * Whether images were included in the search
     */
    private boolean includeImages;
}
