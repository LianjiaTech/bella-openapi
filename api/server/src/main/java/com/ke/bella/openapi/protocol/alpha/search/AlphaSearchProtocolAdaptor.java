package com.ke.bella.openapi.protocol.alpha.search;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;

public interface AlphaSearchProtocolAdaptor extends IProtocolAdaptor {
    String ENDPOINT = "/v1/alpha/search";

    AlphaSearchResponse search(AlphaSearchRequest request, AlphaSearchExecutionContext context);
}
