package com.ke.bella.openapi.protocol.ocr.general;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;

/**
 * 通用OCR识别适配器接口
 */
public interface GeneralAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrGeneralResponse general(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/general";
    }
}
