package com.ke.bella.openapi.protocol.ocr.overseaspassport;

import com.ke.bella.openapi.protocol.ocr.OcrLogHandler;
import org.springframework.stereotype.Component;

@Component
public class OverseasPassportLogHandler extends OcrLogHandler {
    @Override
    public String endpoint() {
        return "/v1/ocr/overseas-passport";
    }
}
