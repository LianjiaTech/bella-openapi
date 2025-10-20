package com.ke.bella.openapi.protocol.ocr;

import org.springframework.stereotype.Component;

@Component
public class OcrIdcardLogHandler extends OcrLogHandler {
    @Override
    public String endpoint() {
        return "/v1/ocr/idcard";
    }
}
