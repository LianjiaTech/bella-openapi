package com.ke.bella.openapi.apikey;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API Key转移日志信息
 *
 * @author claude
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApikeyTransferLog implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * API Key编码
     */
    private String akCode;
    
    /**
     * 原所有者类型
     */
    private String fromOwnerType;
    
    /**
     * 原所有者编码
     */
    private String fromOwnerCode;
    
    /**
     * 原所有者姓名
     */
    private String fromOwnerName;
    
    /**
     * 新所有者类型
     */
    private String toOwnerType;
    
    /**
     * 新所有者编码
     */
    private String toOwnerCode;
    
    /**
     * 新所有者姓名
     */
    private String toOwnerName;
    
    /**
     * 转移原因
     */
    private String transferReason;
    
    /**
     * 转移状态
     */
    private String status;
    
    /**
     * 操作人用户ID
     */
    private Long operatorUid;
    
    /**
     * 操作人姓名
     */
    private String operatorName;
    
    /**
     * 创建时间
     */
    private LocalDateTime ctime;
    
    /**
     * 更新时间
     */
    private LocalDateTime mtime;
}