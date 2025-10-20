package com.ke.bella.openapi.protocol.ocr;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.tmp_idcard.OcrTmpIdcardRequest;
import com.ke.bella.openapi.protocol.ocr.tmp_idcard.OcrTmpIdcardResponse;

public interface OcrTmpIdcardAdaptor<T extends OcrProperty> extends IProtocolAdaptor {
    OcrTmpIdcardResponse tmpIdcard(OcrTmpIdcardRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/tmp_idcard";
    }
}
