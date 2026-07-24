package com.ke.bella.openapi.protocol.ocr.hmt_travel_permit;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrLogHandler;

/**
 * OCR港澳台居民往来大陆/内地通行证日志处理器
 */
@Component
public class HmtTravelPermitLogHandler extends OcrLogHandler {

    @Override
    public String endpoint() {
        return "/v1/ocr/hmt-travel-permit";
    }
}
