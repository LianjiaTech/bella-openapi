package com.ke.bella.openapi.protocol.ocr;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.bankcard.OcrBankcardResponse;

public interface OcrBankcardAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrBankcardResponse bankcard(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/bankcard";
    }
}
