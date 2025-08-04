package com.ke.bella.openapi.protocol.speaker;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Speaker embedding response containing embeddings for audio segments
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@EqualsAndHashCode(callSuper = true)
@Data
public class SpeakerEmbeddingResponse extends OpenapiResponse {
    private String task = "speaker_embedding";
    @JsonProperty("task_id")
    private String taskId = "";
    private double duration = 0;
    private List<Embedding> embeddings;
    private int dimensions = 0;

    @Data
    public static class Embedding {
        private int id = 0;
        private double start = 0;
        private double end = 0;
        private double confidence = 0.0;
        private List<Double> embedding;
        
        public void setStart(double start) {
            this.start = roundToThreeDecimals(start);
        }
        
        public void setEnd(double end) {
            this.end = roundToThreeDecimals(end);
        }
        
        private double roundToThreeDecimals(double value) {
            return BigDecimal.valueOf(value)
                    .setScale(3, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }
}