package com.ke.bella.openapi.protocol.rerank;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.rerank.RerankProperty;
import com.ke.bella.openapi.protocol.rerank.RerankRequest;
import com.ke.bella.openapi.protocol.rerank.RerankResponse;

public interface RerankAdaptor<T extends RerankProperty> extends IProtocolAdaptor {

    RerankResponse rerank(RerankRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/reranks";
    }
}
