package com.ke.bella.openapi.protocol.web;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Web Extract Usage class for billing calculation
 * Independent usage class that tracks extract-specific metrics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebExtractUsage {

    /**
     * Number of URLs successfully extracted
     * Used for extraction cost calculation (per 5 extractions)
     */
    private int successfulExtractions;

    /**
     * Number of URLs that failed to be extracted
     * Failed extractions are not charged
     */
    private int failedExtractions;

    /**
     * The extraction depth used (basic or advanced)
     * Affects extraction cost: basic (1 credit/5 extractions) vs advanced (2
     * credits/5 extractions)
     */
    private String extractDepth;

    /**
     * Whether images were included in the extraction
     */
    private boolean includeImages;

    /**
     * Whether favicons were included in the extraction
     */
    private boolean includeFavicon;
}
