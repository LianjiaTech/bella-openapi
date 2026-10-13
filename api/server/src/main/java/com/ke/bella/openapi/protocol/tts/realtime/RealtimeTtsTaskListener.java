package com.ke.bella.openapi.protocol.tts.realtime;

import java.util.Map;

public interface RealtimeTtsTaskListener {
    void onSpeechStarted(RealtimeTtsTaskContext taskContext);

    void onAudio(RealtimeTtsTaskContext taskContext, byte[] audio, Long durationMs);

    void onTimestamp(RealtimeTtsTaskContext taskContext, RealtimeTtsPayload payload);

    void onCompleted(RealtimeTtsTaskContext taskContext, Map<String, Object> providerUsage);

    void onCancelled(RealtimeTtsTaskContext taskContext, String reason);

    void onFailed(RealtimeTtsTaskContext taskContext, String code, String message, String providerCode, String providerMessage);
}
