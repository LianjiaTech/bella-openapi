package com.ke.bella.openapi.protocol.ocr;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;

import lombok.Data;

/**
 * OCR日志处理器
 */
@Component
public class OcrLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData processData) {
        long startTime = processData.getRequestTime();
        int ttlt = (int) (DateTimeUtils.getCurrentSeconds() - startTime);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("ttlt", ttlt);

        processData.setMetrics(metrics);
        processData.setUsage(1);
    }

    @Override
    public String endpoint() {
        return "/v1/ocr/idcard";
    }
}
