package com.ke.bella.openapi.protocol.ocr.idcard;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;

public interface IdcardAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrIdcardResponse idcard(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/idcard";
    }
}
