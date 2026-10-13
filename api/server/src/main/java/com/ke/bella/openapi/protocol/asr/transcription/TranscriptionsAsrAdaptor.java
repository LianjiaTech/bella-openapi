package com.ke.bella.openapi.protocol.asr.transcription;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.asr.AsrProperty;

public interface TranscriptionsAsrAdaptor<T extends AsrProperty> extends IProtocolAdaptor {

    @Override
    default String endpoint() {
        return "/v1/audio/transcriptions";
    }
}
