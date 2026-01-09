package com.ke.bella.openapi.protocol.embedding;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;

public interface EmbeddingAdaptor<T extends EmbeddingProperty> extends IProtocolAdaptor {

    EmbeddingResponse embedding(EmbeddingRequest request, String url, T property);

    @Override
    default String endpoint() {
        return "/v1/embeddings";
    }
}
