package com.ke.bella.openapi;

import lombok.Data;

import java.util.Set;

@Data
public class PermissionCondition extends PageCondition {
    private String personalCode;
    private Set<String> orgCodes;
    private String projectCode;
}
