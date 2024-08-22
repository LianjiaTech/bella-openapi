package com.ke.bella.openapi.console;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

public class ApikeyOps {
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @SuperBuilder
    public static class ApplyOp extends ConsoleContext.Operator {
        private String ownerType;
        private String ownerCode;
        private String ownerName;
        private String roleCode;
        private BigDecimal monthQuota;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleOp extends ConsoleContext.Operator {
        private String code;
        private String roleCode;
        private List<String> paths;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertifyOp extends ConsoleContext.Operator {
        private String code;
        private String certifyCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuotaOp extends ConsoleContext.Operator {
        private String code;
        private BigDecimal monthQuota;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeOp extends ConsoleContext.Operator {
        private String code;
    }
}
