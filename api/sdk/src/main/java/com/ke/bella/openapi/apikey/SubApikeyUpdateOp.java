package com.ke.bella.openapi.apikey;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SubApikeyUpdateOp {
    private String code;
    private String name;
    private Byte safetyLevel;
    private String outEntityCode;
    private BigDecimal monthQuota;
    private String roleCode;
    private List<String> paths;
    private String remark;
}
