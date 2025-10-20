package com.ke.bella.openapi.protocol.ocr;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.residencepermit.OcrResidencePermitResponse;

public interface OcrResidencePermitAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrResidencePermitResponse residencePermit(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/residence_permit";
    }
}
