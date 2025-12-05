package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public abstract class CompletionProperty implements IProtocolProperty {
    String encodingType = StringUtils.EMPTY;
    boolean mergeReasoningContent = false;
    boolean splitReasoningFromContent = false;
    boolean functionCallSimulate = false;
    Map<String, String> extraHeaders;
    String queueName;

    /**
     * Get authorization property for the channel
     * Must be implemented by subclasses
     */
    public abstract AuthorizationProperty getAuth();

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("encodingType", "编码类型");
        map.put("mergeReasoningContent", "是否合并推理内容");
        map.put("splitReasoningFromContent", "是否需要拆分推理内容");
        map.put("functionCallSimulate", "是否需要强制支持function call");
        map.put("extraHeaders", "额外的请求头");
        map.put("queueName", "队列（配置后请求被bella-job-queue服务代理）");
        return map;
    }
}
