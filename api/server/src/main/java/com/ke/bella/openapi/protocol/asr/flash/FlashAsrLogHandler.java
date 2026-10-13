package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class FlashAsrLogHandler implements EndpointLogHandler {
    @Override
    public void process(EndpointProcessData processData) {
        boolean success = processData.getResponse() != null && processData.getResponse().getError() == null;
        Map<String, Object> usage = new HashMap<>();
        usage.put("count", success ? 1 : 0);
        usage.put("duration", success ? resolveDurationSeconds(processData) : 0.0);
        processData.setUsage(usage);
        int ttlt = (int) (DateTimeUtils.getCurrentSeconds() - processData.getRequestTime());
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("ttlt", ttlt);
        processData.setMetrics(metrics);
        processData.setDuration(ttlt);

    }

    private double resolveDurationSeconds(EndpointProcessData processData) {
        if(processData.getResponse() instanceof FlashAsrResponse) {
            FlashAsrResponse response = (FlashAsrResponse) processData.getResponse();
            if(response.getFlashResult() != null) {
                return response.getFlashResult().getDuration() / 1000.0;
            }
        }
        return 0.0;
    }

    @Override
    public String endpoint() {
        return "/v1/audio/asr/flash";
    }
}
