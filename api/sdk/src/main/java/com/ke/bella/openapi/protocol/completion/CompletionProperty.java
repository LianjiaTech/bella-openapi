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
    boolean stripClaudeCodeAttribution = false;
    Map<String, String> extraHeaders;
    String sessionId;
    String queueName;
    SafetyCheckMode safetyCheckMode = SafetyCheckMode.sync;
    String anthropicVersion;
    String messageEndpointUrl;
    Integer defaultMaxToken;
    String deployName;
    AuthorizationProperty auth;
    boolean inputReplacementEnabled = false;
    Map<String, String> inputReplacementTexts;
    boolean messageSseHeartbeatEnabled = false;
    int messageSseHeartbeatIntervalSeconds = 15;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("encodingType", "编码类型");
        map.put("mergeReasoningContent", "是否合并推理内容");
        map.put("splitReasoningFromContent", "是否需要拆分推理内容");
        map.put("functionCallSimulate", "是否需要强制支持function call");
        map.put("stripClaudeCodeAttribution", "是否在转发Messages请求前移除Claude Code attribution");
        map.put("extraHeaders", "额外的请求头");
        map.put("sessionId", "SessionId Header");
        map.put("queueName", "队列（配置后请求被bella-job-queue服务代理）");
        map.put("safetyCheckMode", "安全检测模式");
        map.put("anthropicVersion", "Anthropic API版本（如2023-06-01，用于原生代理）");
        map.put("messageEndpointUrl", "Message API端点URL（配置后启用Anthropic原生代理）");
        map.put("defaultMaxToken", "默认最大输出token");
        map.put("deployName", "部署名称/模型名称");
        map.put("auth", "鉴权配置");
        map.put("inputReplacementEnabled", "是否开启输入块类型替换");
        map.put("inputReplacementTexts", "输入块类型替换文本映射（key 为 content.type）");
        map.put("messageSseHeartbeatEnabled", "是否开启 Messages SSE 空闲心跳");
        map.put("messageSseHeartbeatIntervalSeconds", "Messages SSE 空闲心跳间隔（秒，最小 1 秒）");
        return map;
    }
}
