package com.ke.bella.openapi.protocol.ocr;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public abstract class YidaoBaseResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("error_code")
    private Integer errorCode;

    @JsonProperty("description")
    private String description;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("recognize_time")
    private Integer recognizeTime;

    @JsonProperty("available_count")
    private Integer availableCount;

    @JsonProperty("result")
    private T result;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldData implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("words")
        private String words;

        @JsonProperty("score")
        private Float score;

        @JsonProperty("chinese_key")
        private String chineseKey;

        @JsonProperty("quad")
        private String quad;

        @JsonProperty("position")
        private PositionData position;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PositionData implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("left")
        private Integer left;

        @JsonProperty("top")
        private Integer top;

        @JsonProperty("width")
        private Integer width;

        @JsonProperty("height")
        private Integer height;
    }
}
