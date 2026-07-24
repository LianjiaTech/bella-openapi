package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.IDescription;
import com.ke.bella.openapi.protocol.completion.CompletionModelProperties;
import com.ke.bella.openapi.protocol.rerank.RerankModelProperties;
import com.ke.bella.openapi.protocol.tts.VoiceProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;

public interface IModelProperties extends IDescription {

    @AllArgsConstructor
    @Getter
    enum EndpointModelPropertyType {
        COMPLETION("/v1/chat/completions", CompletionModelProperties.class),
        RERANK("/v1/reranks", RerankModelProperties.class),
        REALTIME("/v1/audio/realtime", VoiceProperties.class),
        TTS("/v1/audio/speech", VoiceProperties.class),
        REALTIME_TTS("/v1/audio/tts/stream", VoiceProperties.class),
        ;

        private final String endpoint;
        private final Class<? extends IModelProperties> type;

        public static Class<? extends IModelProperties> fetchType(String endpoint) {
            for (EndpointModelPropertyType t : EndpointModelPropertyType.values()) {
                if(t.endpoint.equals(endpoint)) {
                    return t.type;
                }
            }
            return null;
        }

    }
}
