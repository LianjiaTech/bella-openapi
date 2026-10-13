package com.ke.bella.openapi.protocol.tts.realtime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RealtimeTtsEventType {
    START_SPEECH("StartSpeech", true),
    INPUT_TEXT("InputText", true),
    FINISH_SPEECH("FinishSpeech", true),
    CANCEL_SPEECH("CancelSpeech", true),
    CLEAR_TEXT_BUFFER("ClearTextBuffer", true),
    PING("Ping", true),
    SPEECH_STARTED("SpeechStarted", false),
    SPEECH_INPUT_ACK("SpeechInputAck", false),
    SPEECH_AUDIO_DELTA("SpeechAudioDelta", false),
    SPEECH_TIMESTAMP("SpeechTimestamp", false),
    SPEECH_COMPLETED("SpeechCompleted", false),
    SPEECH_CANCELLED("SpeechCancelled", false),
    SPEECH_FAILED("SpeechFailed", false),
    PONG("Pong", false);

    private final String value;
    private final boolean clientEvent;

    public static RealtimeTtsEventType fromString(String value) {
        for (RealtimeTtsEventType eventType : values()) {
            if(eventType.value.equals(value)) {
                return eventType;
            }
        }
        return null;
    }
}
