package com.ke.bella.job.queue.api.entity.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.time.LocalDateTime;
import java.util.Map;

public class BatchParam {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateBatchParam {

        @NotEmpty
        private String inputFileId;

        @NotEmpty
        private String endpoint;

        @NotEmpty
        private String completionWindow;

        private Map<String, String> metadata;

    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetrievesBatchParam {

        @Min(1)
        @Max(100)
        private Integer limit = 20;

        private String after = "";
    }

    @Getter
    @AllArgsConstructor
    public enum CompletionWindowUnit {

        MINUTE("m", "分钟"),
        HOUR("h", "小时"),
        DAY("d", "天");

        private final String code;
        private final String description;

        public static final Integer DEFAULT = 24;

        public static LocalDateTime calculateTime(LocalDateTime localDateTime, int value, String code) {
            switch (code) {
            case "m":
                return localDateTime.plusMinutes(value);
            case "h":
                return localDateTime.plusHours(value);
            case "d":
                return localDateTime.plusDays(value);
            default:
                return localDateTime.plusHours(24); // 默认加24小时
            }
        }

    }
}
