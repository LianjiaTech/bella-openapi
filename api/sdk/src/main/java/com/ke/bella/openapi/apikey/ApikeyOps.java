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
        private Long ownerUserId;  // 通过 userId 查用户，后端自动计算正确的 ownerCode（person 类型时使用）
        private String ownerCode;
        private String ownerName;
        private String roleCode;
        private BigDecimal monthQuota;
        private String remark;
        private String managerCode;
        private String managerName;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ManagerOp extends Operator {
        private String code;
        private Long managerUserId;  // 通过 userId 查用户，后端自动计算正确的 managerCode
        private String managerCode;
        private String managerName;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChangeOwnerOp extends Operator {
        private String code;
        private String targetOwnerType;
        private String targetOwnerCode;
        private String targetOwnerName;
        private String reason;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChangeParentOp extends Operator {
        private String code;
        private String targetParentCode;
        private String reason;
    }

    @Data
    @SuperBuilder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChangeResult {
        private String code;
        private String action;
        private Integer affectedCount;
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
    public static class QpsLimitOp extends Operator {
        private String code;
        /**
         * QPS 限制值
         * null 或 0：使用系统默认值
         * 正数：具体的 QPS 限制
         * 负数：不限制
         */
        private Integer qpsLimit;
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
        private String excludeOwnerType; // 排除指定所有者类型，如传 person 则过滤个人AK
        private String ownerCode;
        private String parentCode;
        private String name;
        private String serviceId;
        private String searchParam; // name / serviceId的模糊搜索
        private String ownerSearch; // ownerName / ownerCode的模糊搜索
        private String managerCode; // 精确匹配管理人
        private String managerSearch; // managerName / managerCode的模糊搜索
        private String outEntityCode;
        private boolean includeChild;
        private boolean onlyChild; // true：仅返回子AK（parent_code != ''），用于管理者视角分页查询子AK
        private String status;
    }
}
