package com.ke.bella.openapi.protocol.systemone;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;

public interface SystemOneAdaptor<T extends SystemOneProperty> extends IProtocolAdaptor {
    SystemOneResponse evaluate(SystemOneRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/systemone";
    }
}
