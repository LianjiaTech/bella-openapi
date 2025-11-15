package com.ke.bella.openapi.protocol.tts;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.RequestMetrics;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.embedding.EmbeddingRequest;
import com.ke.bella.openapi.protocol.tts.TtsRequest;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class TtsLogHandler implements EndpointLogHandler {
    @Override
    public void process(EndpointProcessData processData) {
        long startTime = processData.getRequestTime();
        if(processData.getMetrics() == null) {
            int ttlt = (int) (DateTimeUtils.getCurrentSeconds() - startTime);
            Map<String, Object> map = new HashMap<>();
            map.put("ttlt", ttlt);
            processData.setMetrics(map);
            processData.setDuration(ttlt);
        }

        if(StringUtils.isNotBlank(processData.getRequestRaw())) {
            TtsRequest request = JacksonUtils.deserialize(processData.getRequestRaw(), TtsRequest.class);
            processData.setRequest(request);
        }

        // 获取inputLength - 优先使用预计算的RequestMetrics
        int inputLength = getInputLength(processData);
        processData.setUsage(inputLength);
    }

    /**
     * 获取输入长度 - 简单的double check逻辑
     */
    private int getInputLength(EndpointProcessData processData) {
        // 1. 优先使用预计算的RequestMetrics
        RequestMetrics metrics = processData.getRequestMetrics();
        if (metrics != null && metrics.getInputLength() != null) {
            return metrics.getInputLength();
        }

        // 2. 尝试从原始request获取
        Object request = processData.getRequest();
        if (request instanceof TtsRequest) {
            TtsRequest ttsRequest = (TtsRequest) request;
            return ttsRequest.getInput() != null ? ttsRequest.getInput().length() : 0;
        }

        // 3. Double check: 如果request是null，再检查一次metrics
        // 可能是异步处理刚完成，metrics刚被设置
        if (request == null) {
            metrics = processData.getRequestMetrics(); // 再检查一次
            if (metrics != null && metrics.getInputLength() != null) {
                log.debug("Got inputLength from metrics on double-check: {}", metrics.getInputLength());
                return metrics.getInputLength();
            }
        }

        log.warn("Unable to get inputLength, both RequestMetrics and original request are unavailable. RequestId: {}",
                 processData.getRequestId());
        return 0;
    }

    @Override
    public String endpoint() {
        return "/v1/audio/speech";
    }
}
