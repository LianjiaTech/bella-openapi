package com.ke.bella.openapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Slf4j
public class EndpointProcessData {
    @JsonIgnore
    private String apikey;
    private String akSha;
    private String requestId;
    private String accountType;
    private String accountCode;
    private String akCode;
    private String parentAkCode;
    private String endpoint;
    private String model;
    private String channelCode;
    private boolean isPrivate;
    private String user;
    private long requestMillis;
    private long requestTime; //s
    private long firstPackageTime; //ms
    private long transcriptionDuration;
    private long duration;
    private Object request;
    private String responseRaw;
    private String requestRaw;

    // ========== 请求优化相关字段 ==========
    /**
     * 请求指标数据 - 用于替代大请求对象的轻量级数据
     */
    @JsonIgnore
    private RequestMetrics requestMetrics;

    /**
     * 请求摘要 - 用于日志记录的精简版请求内容
     * -- SETTER --
     *  设置请求摘要

     */
    @Setter
    @JsonIgnore
    private String requestSummary;

    /**
     * 标记request是否已被优化处理
     */
    @JsonIgnore
    private volatile boolean requestOptimized = false;

    private OpenapiResponse response;
    private Object usage;
    private Map<String, Object> metrics = new HashMap<>();
    private String forwardUrl;
    private String protocol;
    private String priceInfo;
    private String encodingType;
    private String supplier;
    private Object requestRiskData;
    private boolean isMock;
    private String bellaTraceId;
    private boolean functionCallSimulate;
    private String channelRequestId;
    private BigDecimal cost;
    private boolean innerLog;
    private Integer maxWaitSec;
    private boolean nativeSend;
    private boolean batch;

    public void setApikeyInfo(ApikeyInfo ak) {
        this.setApikey(ak.getApikey());
        this.setAkCode(ak.getCode());
        this.setParentAkCode(ak.getParentCode());
        this.setAccountType(ak.getOwnerType());
        this.setAccountCode(ak.getOwnerCode());
    }

    // 保存副本，防止日志处理中对response的修改影响返回结果
    public void setResponse(OpenapiResponse response) {
        if(response == null) {
            this.response = null;
            return;
        }
        if(response.supportClone()) {
            try {
                this.response = SerializationUtils.clone(response);
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                this.response = response;
            }
        } else {
            this.response = response;
        }
    }

    // ========== 请求优化相关方法 ==========

    /**
     * 设置request，并标记优化状态
     */
    public void setRequest(Object request) {
        this.request = request;
        this.requestOptimized = false; // 设置新request时重置优化状态
    }

    /**
     * 获取request对象用于序列化到日志
     * 如果已优化，返回摘要；否则返回原始对象
     */
    @JsonProperty("request")
    public Object getRequestForLogging() {
        if (requestOptimized && requestSummary != null) {
            return requestSummary;
        }
        return request;
    }

    /**
     * 获取原始request对象（用于业务逻辑）
     * 大对象会优化为requestSummary，原始为null
     */
    @JsonIgnore
    public Object getRequest() {
        return request;
    }

    /**
     * 标记request已被优化处理
     */
    public void markRequestOptimized() {
        this.requestOptimized = true;
        // 清理原始request引用，帮助GC
        this.request = null;
        log.debug("Request marked as optimized and original reference cleared for requestId: {}", requestId);
    }
}
