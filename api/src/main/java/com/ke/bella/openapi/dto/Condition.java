package com.ke.bella.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Author: Stan Sai Date: 2024/8/2 15:49 description:
 */
public class Condition {
    @Data
    public static class PageCondition {
        private int pageNum;
        private int pageSize;
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class EndpointCondition extends PageCondition {
        private String endpoint;
        private String endpointName;
        private List<String> categoryCode;
        private List<String> endpoints;
        private String maintainerCode;
        private String maintainerName;
        private String status;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class ModelCondition extends PageCondition {
        private String modelName;
        private List<String> modelNames;
        private String visibility;
        private String status;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ChannelCondition extends PageCondition {
        private String channelName;
        private String entityType;
        private String entityCode;
        private String supplier;
        private String protocol;
        private String priority;
        private String dataDestination;
        private String status;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class CategoryCondition extends PageCondition {
        private String categoryCode;
        private String categoryName;
        private String parentCode;
        private Boolean topCategory;
        private String status;
    }

    @Data
    public static class CategoryTreeCondition {
        private String categoryCode;
        private boolean includeEndpoint;
        private String status;
    }
}
