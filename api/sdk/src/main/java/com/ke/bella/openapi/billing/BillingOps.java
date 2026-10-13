package com.ke.bella.openapi.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.PageCondition;
import com.ke.bella.openapi.protocol.cost.CostDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BillingOps {
    public static final String GRANULARITY_DAY = "day";
    public static final String GRANULARITY_MONTH = "month";

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @EqualsAndHashCode(callSuper = true)
    @Data
    public static class BillingRecordCondition extends PageCondition {
        private String granularity;
        private Set<String> akCodes;
        private String startPt;
        private String endPt;
        private Set<String> endpoints;
        private String model;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class BillingAkOption {
        private String code;
        private String name;
        private String akDisplay;
        private String ownerType;
        private String ownerName;
        private String parentCode;
        @Builder.Default
        private List<BillingAkOption> children = new ArrayList<>();
        private boolean hasChildren;
        private boolean directPermission;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class BillingCostRequest {
        private String bellaTraceId;
        private String requestId;
    }

    public static class BillingCostResponse extends CostDetails {
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class BillingRecord {
        private String pt;
        private String endpoint;
        private String model;
        private String accountType;
        private String accountCode;
        private String akCode;
        private BigDecimal amount;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Data
    public static class BillingRecordPage {
        private int page;
        private int pageSize;
        private int total;
        @Builder.Default
        private List<BillingRecord> data = new ArrayList<>();
        private BigDecimal totalAmount;

        @JsonProperty("has_more")
        public boolean hasMore() {
            return page * pageSize < total;
        }

        @JsonProperty("limit")
        public int pageSize() {
            return pageSize;
        }
    }
}
