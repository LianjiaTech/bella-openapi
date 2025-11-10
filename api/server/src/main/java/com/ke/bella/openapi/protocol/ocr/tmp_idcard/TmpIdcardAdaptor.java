package com.ke.bella.openapi.protocol.ocr.tmp_idcard;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;

public interface TmpIdcardAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrTmpIdcardResponse tmpIdcard(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/tmp-idcard";
    }
}
