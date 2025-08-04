package com.ke.bella.openapi.protocol.speaker;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingProperty;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingRequest;
import com.ke.bella.openapi.protocol.speaker.SpeakerEmbeddingResponse;

public interface SpeakerEmbeddingAdaptor extends IProtocolAdaptor {

    SpeakerEmbeddingResponse speakerEmbedding(SpeakerEmbeddingRequest request, String url, SpeakerEmbeddingProperty property);

    @Override
    default String endpoint() {
        return "/v1/audio/speaker/embedding";
    }
}