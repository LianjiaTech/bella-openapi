package com.ke.bella.job.queue.api.entity.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class BatchResp {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchDetailResp {

        private String id;

        private String object;

        private String endpoint;

        private Object errors;

        private String inputFileId;

        private String completionWindow;

        private String status;

        private String outputFileId;

        private String errorFileId;

        private Long createdAt;

        private Long inProgressAt;

        private Long expiresAt;

        private Long finalizingAt;

        private Long completedAt;

        private Long failedAt;

        private Long expiredAt;

        private Long cancellingAt;

        private Long cancelledAt;

        private RequestCounts requestCounts;

        private Map<String, String> metadata;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestCounts {

        private long total;

        private long completed;

        private long failed;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchListResp {

        private String object;

        private List<BatchDetailResp> data;

        private String firstId;

        private String lastId;

        private Boolean hasMore;

    }
}
