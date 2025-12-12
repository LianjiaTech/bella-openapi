package com.ke.bella.openapi.safety;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Builder;
import lombok.Data;

import java.io.IOException;

/**
 * 敏感数据检测请求
 * 新安全接口的请求模型
 */
@Data
@Builder
public class SensitiveAuditRequest {

    /**
     * 请求ID
     */
    @JsonProperty("request_id")
    private String request_id;

    /**
     * 会话ID（可选）
     */
    @JsonProperty("session_id")
    private String session_id;

    /**
     * 时间戳（使用自定义序列化器确保序列化为数字）
     */
    @JsonProperty("timestamp")
    @JsonSerialize(using = LongAsNumberSerializer.class)
    private long timestamp;

    /**
     * Long 类型序列化为数字而不是字符串
     */
    public static class LongAsNumberSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value != null) {
                gen.writeNumber(value);
            }
        }
    }

    /**
     * AI供应商，如 "openai", "aws", "alibaba" 等
     */
    @JsonProperty("provider")
    private String provider;

    /**
     * 客户端IP地址（可选）
     */
    @JsonProperty("client_ip")
    private String client_ip;

    /**
     * API Key code
     */
    @JsonProperty("ak_code")
    private String ak_code;

    /**
     * 服务ID（可选）
     */
    @JsonProperty("service_id")
    private String service_id;

    /**
     * 用户ID
     */
    @JsonProperty("ucid")
    private String ucid;

    /**
     * LLM协议负载，包含完整的请求或响应对象
     * input阶段：CompletionRequest
     * output阶段：CompletionResponse
     */
    @JsonProperty("llm_payload")
    private Object llm_payload;
}