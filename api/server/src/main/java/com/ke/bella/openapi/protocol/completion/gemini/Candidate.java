package com.ke.bella.openapi.protocol.completion.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Candidate {
    private Content content;
    private String finishReason;
    private List<SafetyRating> safetyRatings;
    private CitationMetadata citationMetadata;
    private Double avgLogprobs;
    private LogprobsResult logprobsResult;
    private Integer index;
}
