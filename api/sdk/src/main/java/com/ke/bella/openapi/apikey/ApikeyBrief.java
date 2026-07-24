package com.ke.bella.openapi.apikey;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApikeyBrief implements Serializable {
    private static final long serialVersionUID = 1L;
    private String code;
    private String name;
    private String serviceId;
    private String managerCode;
    private String managerName;
    private String ownerType;
    private String ownerCode;
    private String ownerName;
    private String remark;
}
