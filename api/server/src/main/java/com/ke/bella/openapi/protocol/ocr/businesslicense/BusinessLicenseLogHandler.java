package com.ke.bella.openapi.protocol.ocr.businesslicense;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.protocol.ocr.OcrLogHandler;

@Component
public class BusinessLicenseLogHandler extends OcrLogHandler {
    @Override
    public String endpoint() {
        return "/v1/ocr/business-license";
    }
}
