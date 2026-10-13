package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RealtimeTtsLogHandler implements EndpointLogHandler {
    @Override
    public void process(EndpointProcessData processData) {
        if(processData.getUsage() == null && processData.getMetrics() != null) {
            Object inputCharacters = processData.getMetrics().get("input_characters");
            if(inputCharacters instanceof Number) {
                processData.setUsage(((Number) inputCharacters).intValue());
            }
        }
        if(processData.getUsage() instanceof Map) {
            Map<?, ?> usage = (Map<?, ?>) processData.getUsage();
            Object inputCharacters = usage.get("input_characters");
            if(inputCharacters instanceof Number) {
                processData.setUsage(((Number) inputCharacters).intValue());
            }
        }
    }

    @Override
    public String endpoint() {
        return RealtimeTtsConstants.ENDPOINT;
    }
}
