package com.ke.bella.openapi.protocol.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.ke.bella.openapi.utils.JacksonUtils;

public class HuoshanStreamTtsCallbackTest {

    @Test
    public void convertsSpeedToSpeechRate() {
        TtsRequest request = requestWithSpeed(1.5);

        Map<String, Object> audioParams = audioParams(request);

        assertEquals(50, audioParams.get("speech_rate"));
    }

    @Test
    public void doesNotSendDefaultSpeedAsSpeechRate() {
        TtsRequest request = requestWithSpeed(1.0);

        Map<String, Object> audioParams = audioParams(request);

        assertFalse(audioParams.containsKey("speech_rate"));
    }

    @Test
    public void requestAudioParamsSpeechRateOverridesSpeed() {
        TtsRequest request = requestWithSpeed(1.5);
        Map<String, Object> requestExtra = new HashMap<>();
        Map<String, Object> audioParams = new HashMap<>();
        audioParams.put("speech_rate", 12);
        requestExtra.put("audio_params", audioParams);
        request.setExtraBodyField("request", requestExtra);

        Map<String, Object> payloadAudioParams = audioParams(request);

        assertEquals(12, payloadAudioParams.get("speech_rate"));
    }

    @Test
    public void audioParamsSpeechRateOverridesSpeed() {
        TtsRequest request = requestWithSpeed(1.5);
        Map<String, Object> audioParams = new HashMap<>();
        audioParams.put("speech_rate", -30);
        request.setExtraBodyField("audio_params", audioParams);

        Map<String, Object> payloadAudioParams = audioParams(request);

        assertEquals(-30, payloadAudioParams.get("speech_rate"));
    }

    private TtsRequest requestWithSpeed(Double speed) {
        TtsRequest request = new TtsRequest();
        request.setInput("hello");
        request.setSpeed(speed);
        request.setResponseFormat("wav");
        return request;
    }

    private Map<String, Object> audioParams(TtsRequest request) {
        HuoshanStreamTtsCallback.PayloadJ payload = new HuoshanStreamTtsCallback.PayloadJ(request, 200);
        Map<String, Object> payloadMap = JacksonUtils.toMap(JacksonUtils.serialize(payload));
        Map<String, Object> reqParams = (Map<String, Object>) payloadMap.get("req_params");
        return (Map<String, Object>) reqParams.get("audio_params");
    }
}
