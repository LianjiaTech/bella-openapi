package com.ke.bella.openapi.protocol.ocr.residence_permit;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrLogHandler;

/**
 * OCR港澳台居民居住证日志处理器
 */
@Component
public class ResidencePermitLogHandler extends OcrLogHandler {

    @Override
    public String endpoint() {
        return "/v1/ocr/hmt-residence-permit";
    }
}
