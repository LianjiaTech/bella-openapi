package com.ke.bella.openapi.protocol.ocr.tmp_idcard;

import com.ke.bella.openapi.protocol.ocr.OcrLogHandler;
import org.springframework.stereotype.Component;

@Component
public class TmpIdcardLogHandler extends OcrLogHandler {
    @Override
    public String endpoint() {
        return "/v1/ocr/tmp-idcard";
    }
}
