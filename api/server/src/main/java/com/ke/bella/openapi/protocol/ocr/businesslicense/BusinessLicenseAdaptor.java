package com.ke.bella.openapi.protocol.ocr.businesslicense;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;

public interface BusinessLicenseAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrBusinessLicenseResponse businessLicense(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/business-license";
    }
}
