package com.ke.bella.openapi.protocol.asr.diarization;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.protocol.asr.AudioTranscriptionRequest.AudioTranscriptionReq;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationResponse;
import com.ke.bella.openapi.protocol.asr.diarization.SpeakerDiarizationProperty;

public interface SpeakerDiarizationAdaptor<T extends SpeakerDiarizationProperty> extends IProtocolAdaptor {

    @Override
    default String endpoint() {
        return "/v1/audio/speaker/diarization";
    }

    SpeakerDiarizationResponse speakerDiarization(AudioTranscriptionReq request, String url, T property);
}