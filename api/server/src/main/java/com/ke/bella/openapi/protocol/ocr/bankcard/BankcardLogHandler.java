package com.ke.bella.openapi.protocol.ocr.bankcard;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrLogHandler;

@Component
public class BankcardLogHandler extends OcrLogHandler {
    @Override
    public String endpoint() {
        return "/v1/ocr/bankcard";
    }
}
