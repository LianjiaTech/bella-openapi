package com.ke.bella.openapi.protocol.ocr.idcard;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrLogHandler;

@Component
public class IdcardLogHandler extends OcrLogHandler {
    @Override
    public String endpoint() {
        return "/v1/ocr/idcard";
    }
}
