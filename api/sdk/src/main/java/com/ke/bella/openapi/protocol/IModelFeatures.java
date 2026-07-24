package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.IDescription;
import com.ke.bella.openapi.protocol.completion.CompletionModelFeatures;
import com.ke.bella.openapi.protocol.rerank.RerankModelFeatures;
import com.ke.bella.openapi.protocol.tts.TTSModelFeatures;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsModelFeatures;
import com.ke.bella.openapi.protocol.images.ImagesModelFeatures;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface IModelFeatures extends IDescription {

    @AllArgsConstructor
    @Getter
    enum EndpointModelFeatureType {
        COMPLETION("/v1/chat/completions", CompletionModelFeatures.class),
        RERANK("/v1/reranks", RerankModelFeatures.class),
        TTS("/v1/audio/speech", TTSModelFeatures.class),
        REALTIME_TTS("/v1/audio/tts/stream", RealtimeTtsModelFeatures.class),
        IMAGES("/v1/images/generations", ImagesModelFeatures.class);

        private final String endpoint;
        private final Class<? extends IModelFeatures> type;

        public static Class<? extends IModelFeatures> fetchType(String endpoint) {
            for (EndpointModelFeatureType t : EndpointModelFeatureType.values()) {
                if(t.endpoint.equals(endpoint)) {
                    return t.type;
                }
            }
            return null;
        }

    }
}
