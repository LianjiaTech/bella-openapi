package com.ke.bella.openapi.protocol.ocr.tmp_idcard;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class YidaoOcrTmpIdcardResponse {

    private Result result;

    @JsonProperty("error_code")
    private Integer errorCode;

    private String description;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("recognize_time")
    private Integer recognizeTime;

    @JsonProperty("available_count")
    private Integer availableCount;

    @Data
    public static class Result {
        private FieldInfo name;
        private FieldInfo gender;
        private FieldInfo nationality;
        private FieldInfo birthdate;
        private FieldInfo address;
        private FieldInfo idno;
        private FieldInfo issued;
        private FieldInfo valid;
    }

    @Data
    public static class FieldInfo {
        private String words;
        private Float score;
        private Position position;
        @JsonProperty("chinese_key")
        private String chineseKey;
        private String quad;
    }

    @Data
    public static class Position {
        private Integer left;
        private Integer top;
        private Integer width;
        private Integer height;
    }
}
