package com.ke.bella.openapi.protocol.metadata;

import java.util.Set;

import com.ke.bella.openapi.protocol.PageCondition;
import com.ke.bella.openapi.protocol.PermissionCondition;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

public class Condition {

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class EndpointCondition extends PageCondition {
        private String endpoint;
        private String endpointCode;
        private String endpointName;
        private Set<String> categoryCode;
        private Set<String> endpoints;
        private String maintainerCode;
        private String maintainerName;
        private String status;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class ModelCondition extends PermissionCondition {
        private String modelName;
        private String endpoint;
        private Set<String> modelNames;
        private String visibility;
        private String status;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ChannelCondition extends PageCondition {
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
