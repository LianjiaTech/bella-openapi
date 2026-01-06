package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import com.ke.bella.openapi.safety.SafetyCheckMode;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class CompletionProperty implements IProtocolProperty {
    String encodingType = StringUtils.EMPTY;
    boolean mergeReasoningContent = false;
    boolean splitReasoningFromContent = false;
    boolean functionCallSimulate = false;
    Map<String, String> extraHeaders;
    String queueName;
    String safetyCheckMode = SafetyCheckMode.async.name();
    String anthropicVersion;
    String messageEndpointUrl;
    Integer defaultMaxToken;
    String deployName;
    AuthorizationProperty auth;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("encodingType", "编码类型");
        map.put("mergeReasoningContent", "是否合并推理内容");
        map.put("splitReasoningFromContent", "是否需要拆分推理内容");
        map.put("functionCallSimulate", "是否需要强制支持function call");
        map.put("extraHeaders", "额外的请求头");
        map.put("queueName", "队列（配置后请求被bella-job-queue服务代理）");
        map.put("anthropicVersion", "Anthropic API版本（如2023-06-01，用于原生代理）");
        map.put("messageEndpointUrl", "Message API端点URL（配置后启用Anthropic原生代理）");
        map.put("defaultMaxToken", "默认最大输出token");
        map.put("deployName", "部署名称/模型名称");
        map.put("auth", "鉴权配置");
        return map;
    }
}
