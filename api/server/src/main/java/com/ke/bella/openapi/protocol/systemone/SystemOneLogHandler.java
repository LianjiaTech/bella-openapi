package com.ke.bella.openapi.protocol.systemone;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class SystemOneLogHandler implements EndpointLogHandler {
    @Override
    public void process(EndpointProcessData processData) {
        SystemOneResponse response = null;
        if(processData.getResponse() instanceof SystemOneResponse) {
            response = (SystemOneResponse) processData.getResponse();
        }
        if(StringUtils.isNotBlank(processData.getResponseRaw()) && response == null) {
            response = JacksonUtils.deserialize(processData.getResponseRaw(), SystemOneResponse.class);
        }
        if(StringUtils.isNotBlank(processData.getRequestRaw())) {
            processData.setRequest(JacksonUtils.deserialize(processData.getRequestRaw(), SystemOneRequest.class));
        }

        SystemOneUsage usage = response == null ? null : response.getUsage();
        if(usage == null) {
            usage = new SystemOneUsage();
        }
        Integer totalTokens = usage.getTotalTokens();
        if(totalTokens == null) {
            totalTokens = 0;
        }
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("ttlt", (int) (DateTimeUtils.getCurrentSeconds() - processData.getRequestTime()));
        metrics.put("token", totalTokens);
        processData.setMetrics(metrics);
        processData.setUsage(usage);
    }

    @Override
    public String endpoint() {
        return "/v1/systemone";
    }
}
