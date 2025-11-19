package com.ke.bella.openapi.protocol.ocr.general;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrLogHandler;

@Component
public class GeneralLogHandler extends OcrLogHandler {
    @Override
    public String endpoint() {
        return "/v1/ocr/general";
    }
}
