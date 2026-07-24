package com.ke.bella.openapi.protocol.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Web Crawl Usage class for billing calculation
 * Independent usage class that tracks crawl-specific metrics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebCrawlUsage {

    /**
     * Number of pages successfully mapped
     * Used for mapping cost calculation (per 10 pages)
     */
    private int pagesMapped;

    /**
     * Whether natural language instructions were provided
     * Affects mapping cost: regular mapping (1 credit/10 pages) vs instruction
     * mapping (2 credits/10 pages)
     */
    private boolean hasInstructions;

    /**
     * Number of successful URL extractions
     * Used for extraction cost calculation (per 5 extractions)
     */
    private int successfulExtractions;

    /**
     * The extraction depth used (basic or advanced)
     * Affects extraction cost: basic (1 credit/5 extractions) vs advanced (2
     * credits/5 extractions)
     */
    private String extractDepth;
}
