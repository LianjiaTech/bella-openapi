package com.ke.bella.openapi.metadata;

import com.ke.bella.openapi.PageCondition;
import com.ke.bella.openapi.PermissionCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

public class Condition {

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class EndpointDetailsCondition  {
        private String endpoint;
        private String modelName;
        private List<String> features;
    }

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
        private String ownerName;
        private String endpoint;
        private Set<String> endpoints;
        private Set<String> modelNames;
        private String visibility;
        private String status;
        private Integer maxInputTokensLimit;
        private Integer maxOutputTokensLimit;
        private List<String> features;
        private String dataDestination;
        private String supplier;
        private boolean includeLinkedTo;
        private boolean includePrice = false;
    }

    @EqualsAndHashCode(callSuper = true)
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ChannelCondition extends PageCondition {
        private String entityType;
        private String entityCode;
        private Set<String> entityCodes;
        private String supplier;
        private String protocol;
        private String priority;
        private String dataDestination;
        private String status;
        private String visibility;
        private String ownerType;
        private String ownerCode;
        private String queueName;
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
