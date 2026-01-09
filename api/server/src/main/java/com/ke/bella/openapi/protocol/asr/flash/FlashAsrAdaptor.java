package com.ke.bella.openapi.protocol.asr.flash;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.asr.AsrRequest;

public interface FlashAsrAdaptor<T extends AsrProperty> extends IProtocolAdaptor {
    FlashAsrResponse asr(AsrRequest request, String url, T property, EndpointProcessData processData);

    @Override
    default String endpoint() {
        return "/v1/audio/asr/flash";
    }
}
