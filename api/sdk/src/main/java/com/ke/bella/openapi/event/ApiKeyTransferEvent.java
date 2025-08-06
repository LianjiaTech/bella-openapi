package com.ke.bella.openapi.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Key转移事件
 *
 * @author claude
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyTransferEvent {
    
    /**
     * API Key编码
     */
    private String akCode;
    
    /**
     * 原所有者信息
     */
    private String fromOwnerCode;
    private String fromOwnerName;
    
    /**
     * 新所有者信息
     */
    private String toOwnerCode;
    private String toOwnerName;
    
    /**
     * 转移原因
     */
    private String transferReason;
    
    /**
     * 操作者信息
     */
    private Long operatorUid;
    private String operatorName;
    
    public static ApiKeyTransferEvent of(String akCode, String fromOwnerCode, String fromOwnerName,
                                        String toOwnerCode, String toOwnerName, String transferReason,
                                        Long operatorUid, String operatorName) {
        return new ApiKeyTransferEvent(akCode, fromOwnerCode, fromOwnerName, 
                                     toOwnerCode, toOwnerName, transferReason,
                                     operatorUid, operatorName);
    }
}