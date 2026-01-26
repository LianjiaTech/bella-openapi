package com.ke.bella.openapi.protocol.ocr.provider.ke;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class KeResponse {

    private int code;
    private String message;
    private String requestId;
    private String orderId;
    private String serverTime;
    private Long timeCost;
    private Result result;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private Map<String, Object> others;
        private List<StructuredResult> structuredResult;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructuredResult {
        private String enKeyName;
        private String keyName;
        private String value;
    }
}
