package com.ke.bella.openapi.protocol.tts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.ke.bella.openapi.utils.JacksonUtils;

public class HuoShanV3RequestTest {

    @Test
    public void audioParamsExtraFieldsPassThroughAndSpeechRateOverridesSpeed() {
        TtsRequest request = baseRequest();
        request.setSpeed(1.5);

        Map<String, Object> audioParams = new HashMap<>();
        audioParams.put("emotion", "happy");
        audioParams.put("enable_timestamp", true);
        audioParams.put("speech_rate", 12);
        request.setExtraBodyField("audio_params", audioParams);

        Map<String, Object> serializedAudioParams = audioParams(reqParams(serialized(request)));

        assertEquals("wav", serializedAudioParams.get("format"));
        assertEquals(16000, serializedAudioParams.get("sample_rate"));
        assertEquals(12, serializedAudioParams.get("speech_rate"));
        assertEquals("happy", serializedAudioParams.get("emotion"));
        assertEquals(true, serializedAudioParams.get("enable_timestamp"));
    }

    @Test
    public void topLevelAudioParamsFromJsonPassThroughAndOverrideSpeed() {
        String json = "{"
                + "\"model\":\"Doubao_Seed_TTS_2\","
                + "\"input\":\"hello\","
                + "\"speed\":3,"
                + "\"stream\":false,"
                + "\"response_format\":\"mp3\","
                + "\"audio_params\":{\"speech_rate\":20,\"emotion\":\"happy\"}"
                + "}";
        TtsRequest request = JacksonUtils.deserialize(json, TtsRequest.class);

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> serializedAudioParams = audioParams(reqParams(serialized));

        assertFalse(serialized.containsKey("audio_params"));
        assertFalse(serialized.containsKey("extra_body"));
        assertEquals(20, serializedAudioParams.get("speech_rate"));
        assertEquals("happy", serializedAudioParams.get("emotion"));
    }

    @Test
    public void wrappedExtraBodyAudioParamsPassThroughAndOverrideSpeed() {
        TtsRequest request = baseRequest();
        request.setSpeed(3.0);

        Map<String, Object> audioParams = new HashMap<>();
        audioParams.put("speech_rate", 20);
        audioParams.put("emotion", "happy");
        Map<String, Object> wrappedExtraBody = new HashMap<>();
        wrappedExtraBody.put("audio_params", audioParams);
        request.setExtraBodyField("extra_body", wrappedExtraBody);

        Map<String, Object> serialized = serialized(request);
        Map<String, Object> serializedAudioParams = audioParams(reqParams(serialized));

        assertFalse(serialized.containsKey("extra_body"));
        assertEquals(20, serializedAudioParams.get("speech_rate"));
        assertEquals("happy", serializedAudioParams.get("emotion"));
    }

    @Test
    public void reqParamsExtraFieldsPassThroughWithoutBreakingDefaultMappings() {
        TtsRequest request = baseRequest();

        Map<String, Object> context = new HashMap<>();
        context.put("scene", "narration");
        Map<String, Object> nestedAudioParams = new HashMap<>();
        nestedAudioParams.put("format", "ogg");
        nestedAudioParams.put("sample_rate", 8000);
        nestedAudioParams.put("emotion", "sad");
        Map<String, Object> reqParams = new HashMap<>();
        reqParams.put("text", "overridden text");
        reqParams.put("speaker", "overridden speaker");
        reqParams.put("audio_params", nestedAudioParams);
        reqParams.put("language", "zh");
        reqParams.put("context", context);
        request.setExtraBodyField("req_params", reqParams);

        Map<String, Object> serializedReqParams = reqParams(serialized(request));

        assertEquals("hello", serializedReqParams.get("text"));
        assertEquals("bella_voice", serializedReqParams.get("speaker"));
        assertEquals("zh", serializedReqParams.get("language"));
        assertEquals(context, serializedReqParams.get("context"));

        Map<String, Object> serializedAudioParams = audioParams(serializedReqParams);
        assertEquals("ogg", serializedAudioParams.get("format"));
        assertEquals(8000, serializedAudioParams.get("sample_rate"));
        assertEquals("sad", serializedAudioParams.get("emotion"));
    }

    @Test
    public void audioParamsShorthandOverridesNestedReqParamsAudioParams() {
        TtsRequest request = baseRequest();

        Map<String, Object> nestedAudioParams = new HashMap<>();
        nestedAudioParams.put("format", "ogg");
        nestedAudioParams.put("speech_rate", 10);
        Map<String, Object> reqParams = new HashMap<>();
        reqParams.put("audio_params", nestedAudioParams);
        request.setExtraBodyField("req_params", reqParams);

        Map<String, Object> audioParams = new HashMap<>();
        audioParams.put("format", "mp3");
        audioParams.put("speech_rate", 30);
        request.setExtraBodyField("audio_params", audioParams);

        Map<String, Object> serializedAudioParams = audioParams(reqParams(serialized(request)));

        assertEquals("mp3", serializedAudioParams.get("format"));
        assertEquals(30, serializedAudioParams.get("speech_rate"));
    }

    @Test
    public void rootExtraFieldsPassThroughAndGeneratedFieldsWinConflicts() {
        TtsRequest request = baseRequest();
        request.setUser("bella-user");

        Map<String, Object> trace = new HashMap<>();
        trace.put("span_id", "span-1");
        Map<String, Object> conflictingUser = new HashMap<>();
        conflictingUser.put("uid", "extra-user");
        Map<String, Object> conflictingReqParams = new HashMap<>();
        conflictingReqParams.put("text", "extra text");
        Map<String, Object> rootAudioParams = new HashMap<>();
        rootAudioParams.put("speech_rate", 20);

        request.setExtraBodyField("trace", trace);
        request.setExtraBodyField("namespace", "tts2");
        request.setExtraBodyField("user", conflictingUser);
        request.setExtraBodyField("req_params", conflictingReqParams);
        request.setExtraBodyField("audio_params", rootAudioParams);

        Map<String, Object> serialized = serialized(request);

        assertEquals(trace, serialized.get("trace"));
        assertEquals("tts2", serialized.get("namespace"));
        assertFalse(serialized.containsKey("audio_params"));

        Map<String, Object> user = map(serialized.get("user"));
        assertEquals("bella-user", user.get("uid"));

        Map<String, Object> reqParams = reqParams(serialized);
        assertEquals("hello", reqParams.get("text"));
        assertEquals(20, audioParams(reqParams).get("speech_rate"));
    }

    @Test
    public void speedConvertsToSpeechRateWhenAudioParamsDoesNotOverride() {
        TtsRequest request = baseRequest();
        request.setSpeed(1.5);

        assertEquals(50, audioParams(reqParams(serialized(request))).get("speech_rate"));
    }

    private TtsRequest baseRequest() {
        TtsRequest request = new TtsRequest();
        request.setInput("hello");
        request.setVoice("bella_voice");
        request.setResponseFormat("wav");
        request.setSampleRate(16000);
        return request;
    }

    private HuoShanV3Property property() {
        HuoShanV3Property property = new HuoShanV3Property();
        property.setDefaultContentType("mp3");
        property.setDefaultSampleRate(24000);
        property.setDefaultVoice("default_voice");
        return property;
    }

    private Map<String, Object> serialized(TtsRequest request) {
        return JacksonUtils.toMap(JacksonUtils.serialize(HuoShanV3Request.from(request, property())));
    }

    private Map<String, Object> reqParams(Map<String, Object> request) {
        return map(request.get("req_params"));
    }

    private Map<String, Object> audioParams(Map<String, Object> reqParams) {
        return map(reqParams.get("audio_params"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
