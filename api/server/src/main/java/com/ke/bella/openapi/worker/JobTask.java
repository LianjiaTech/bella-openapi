package com.ke.bella.openapi.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job Queue中的任务定义
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobTask {
    
    /**
     * 任务ID
     */
    private String taskId;
    
    /**
     * 任务类型/端点
     */
    private String endpoint;
    
    /**
     * 目标channel code
     */
    private String channelCode;
    
    /**
     * 任务数据
     */
    private Object taskData;
    
    /**
     * API Key
     */
    private String apiKey;
    
    /**
     * 任务创建时间
     */
    private Long createTime;
    
    /**
     * 任务超时时间（秒）
     */
    private Integer timeout;
    
    /**
     * 任务优先级
     */
    private Integer priority;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetries;
}