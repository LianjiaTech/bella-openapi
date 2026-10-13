package com.ke.bella.openapi.protocol.ocr.hmt_travel_permit;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.ocr.OcrProperty;
import com.ke.bella.openapi.protocol.ocr.OcrRequest;
import com.ke.bella.openapi.protocol.ocr.hmttravelpermit.OcrHmtTravelPermitResponse;

public interface HmtTravelPermitAdaptor<T extends OcrProperty> extends IProtocolAdaptor {

    OcrHmtTravelPermitResponse hmtTravelPermit(OcrRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/ocr/hmt-travel-permit";
    }
}
