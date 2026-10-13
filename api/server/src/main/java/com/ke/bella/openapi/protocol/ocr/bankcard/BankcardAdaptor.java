package com.ke.bella.openapi.protocol.ocr.bankcard;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;

public interface BankcardAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrBankcardResponse bankcard(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/bankcard";
    }
}
