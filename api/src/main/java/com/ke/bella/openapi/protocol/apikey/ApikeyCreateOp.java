package com.ke.bella.openapi.protocol.apikey;

import com.ke.bella.openapi.console.ConsoleContext;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ApikeyCreateOp extends ConsoleContext.Operator {
    private String parentCode;
    private Byte safetyLevel;
    private BigDecimal monthQuota;
    private String roleCode;
    private List<String> paths;
}
