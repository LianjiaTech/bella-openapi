package com.ke.bella.openapi.protocol.realtime;

import com.ke.bella.openapi.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;

@Component
public class RealTimeLogHandler implements EndpointLogHandler {
    @Override
    public void process(EndpointProcessData processData) {
        processData.setDuration(DateTimeUtils.getCurrentSeconds() - processData.getRequestTime());
        // Prefer provider-derived audio duration. The connection duration is only
        // a fallback for providers that return no usable timing information.
        long transcriptionDuration = processData.getTranscriptionDuration();
        processData.setUsage(transcriptionDuration > 0 ? transcriptionDuration : processData.getDuration());
    }

    @Override
    public String endpoint() {
        return "/v1/audio/realtime";
    }
}
