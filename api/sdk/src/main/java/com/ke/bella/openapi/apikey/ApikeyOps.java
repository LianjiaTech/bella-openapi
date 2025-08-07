package com.ke.bella.openapi.apikey;

import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.PageCondition;
import com.ke.bella.openapi.PermissionCondition;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

public class ApikeyOps {
    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ApplyOp extends Operator {
        private String name;
        private String ownerType;
        private String ownerCode;
        private String ownerName;
        private String roleCode;
        private BigDecimal monthQuota;
        private String remark;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class NameOp extends Operator {
        private String code;
        private String name;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ServiceOp extends Operator {
        private String code;
        private String serviceId;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleOp extends Operator {
        private String code;
        private String roleCode;
        private List<String> paths;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CertifyOp extends Operator {
        private String code;
        private String certifyCode;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class QuotaOp extends Operator {
        private String code;
        private BigDecimal monthQuota;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CodeOp extends Operator {
        private String code;
    }

    @Data
    public static class ApikeyCondition extends PermissionCondition {
        private String ownerType;
        private String ownerCode;
        private String parentCode;
        private String name;
        private String serviceId;
        private String searchParam; // name / serviceId的模糊搜索
        private String outEntityCode;
        private boolean includeChild;
        private String status;
    }
}
