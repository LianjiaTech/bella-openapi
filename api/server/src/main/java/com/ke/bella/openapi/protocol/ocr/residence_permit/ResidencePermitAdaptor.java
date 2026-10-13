package com.ke.bella.openapi.protocol.ocr.residence_permit;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.residencepermit.OcrResidencePermitResponse;

public interface ResidencePermitAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrResidencePermitResponse hmtResidencePermit(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/hmt-residence-permit";
    }
}
