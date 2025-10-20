package com.ke.bella.openapi.protocol.ocr;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.idcard.OcrIdcardResponse;

public interface OcrIdcardAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrIdcardResponse idcard(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/idcard";
    }
}
