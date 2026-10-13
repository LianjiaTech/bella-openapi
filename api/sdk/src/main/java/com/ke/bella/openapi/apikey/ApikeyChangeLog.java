package com.ke.bella.openapi.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApikeyChangeLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String actionType;
    private String akCode;
    private String affectedCodes;
    private String fromOwnerType;
    private String fromOwnerCode;
    private String fromOwnerName;
    private String toOwnerType;
    private String toOwnerCode;
    private String toOwnerName;
    private String fromParentCode;
    private String toParentCode;
    private String fromManagerCode;
    private String fromManagerName;
    private String toManagerCode;
    private String toManagerName;
    private String reason;
    private String status;
    private Long operatorUid;
    private String operatorName;
    private LocalDateTime ctime;
    private LocalDateTime mtime;
}
