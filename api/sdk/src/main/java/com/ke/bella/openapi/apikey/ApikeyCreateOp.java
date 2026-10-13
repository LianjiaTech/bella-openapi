package com.ke.bella.openapi.apikey;

import com.ke.bella.openapi.Operator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

@Data
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class ApikeyCreateOp extends Operator {
    private String name;
    private String parentCode;
    private Byte safetyLevel;
    private String outEntityCode;
    private BigDecimal monthQuota;
    private String roleCode;
    private List<String> paths;
    private String remark;
}
