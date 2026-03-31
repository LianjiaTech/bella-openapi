package com.ke.bella.openapi.protocol.ocr.overseas_passport;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;

public interface OverseasPassportAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrOverseasPassportResponse overseasPassport(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/overseas-passport";
    }
}
