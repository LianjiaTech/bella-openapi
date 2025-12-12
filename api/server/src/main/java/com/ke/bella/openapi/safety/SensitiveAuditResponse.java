package com.ke.bella.openapi.safety;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 敏感数据检测响应
 * 新安全接口的响应模型
 */
@Data
public class SensitiveAuditResponse {

    /**
     * 错误码，"0" 表示成功
     */
    @JsonProperty("errno")
    private String errno;

    /**
     * 错误消息
     */
    @JsonProperty("errmsg")
    private String errmsg;

    /**
     * 请求ID
     */
    @JsonProperty("request_id")
    private String request_id;

    /**
     * 检测到包含敏感数据的结果列表
     * 空数组表示未检测到敏感数据
     * 非空表示检测到敏感数据
     */
    @JsonProperty("results")
    private List<SensitiveResult> results;
}