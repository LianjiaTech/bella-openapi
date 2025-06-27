package com.ke.bella.job.queue.api.entity.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class RegisterResp {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointRespData {
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterEndpointResp {
        private EndpointRespData data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelRespData {
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegisterModelResp {
        private ModelRespData data;
    }
}
