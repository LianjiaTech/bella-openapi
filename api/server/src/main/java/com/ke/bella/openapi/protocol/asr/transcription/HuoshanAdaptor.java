package com.ke.bella.openapi.protocol.asr.transcription;

import com.ke.bella.openapi.protocol.asr.AsrProperty;
import org.springframework.stereotype.Component;

@Component("HuoshanTranscriptionsAsr")
public class HuoshanAdaptor implements TranscriptionsAsrAdaptor<AsrProperty> {
    @Override
    public String getDescription() {
        return "火山协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return AsrProperty.class;
    }
}
