package com.ke.bella.openapi.protocol.tts.realtime.huoshan;

import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsHeader;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsMessage;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsPayload;
import com.ke.bella.openapi.utils.JacksonUtils;
import okio.ByteString;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HuoshanTtsWsCodecTest {
    private final HuoshanTtsWsCodec codec = new HuoshanTtsWsCodec();

    @Test
    public void separatesStartSessionConfigFromTaskRequestText() {
        RealtimeTtsPayload config = RealtimeTtsPayload.builder()
                .voice("zh_female_xxx")
                .format("pcm")
                .sampleRate(24000)
                .channels(1)
                .encoding("s16le")
                .speed(1.5)
                .volume(1.2)
                .pitch(0.9)
                .textType("plain")
                .enableTimestamp(true)
                .build();

        Map<String, Object> startPayload = payload(codec.startSessionFrame("session1", config));
        Map<String, Object> startReqParams = (Map<String, Object>) startPayload.get("req_params");
        Map<String, Object> audioParams = (Map<String, Object>) startReqParams.get("audio_params");
        Map<String, Object> additions = additions(startReqParams);
        Map<String, Object> postProcess = (Map<String, Object>) additions.get("post_process");
        assertTrue(startReqParams.containsKey("audio_params"));
        assertFalse(startReqParams.containsKey("text"));
        assertEquals(true, audioParams.get("enable_timestamp"));
        assertEquals(true, audioParams.get("enable_subtitle"));
        assertEquals(20, audioParams.get("loudness_rate"));
        assertFalse(audioParams.containsKey("volume_ratio"));
        assertEquals(-10, postProcess.get("pitch"));
        assertFalse(audioParams.containsKey("pitch_ratio"));

        RealtimeTtsMessage input = RealtimeTtsMessage.builder()
                .header(RealtimeTtsHeader.builder().name("InputText").taskId("task1").build())
                .payload(RealtimeTtsPayload.builder().text("hello").textType("plain").build())
                .build();
        Map<String, Object> taskPayload = payload(codec.taskRequestFrame("session1", input));
        Map<String, Object> taskReqParams = (Map<String, Object>) taskPayload.get("req_params");
        assertTrue(taskReqParams.containsKey("text"));
        assertFalse(taskReqParams.containsKey("audio_params"));
        assertFalse(taskReqParams.containsKey("speaker"));
        assertFalse(taskReqParams.containsKey("additions"));
    }

    @Test
    public void badFrameReturnsStructuredError() {
        HuoshanTtsWsCodec.HuoshanTtsWsEvent event = codec.parse(new byte[] { 1, 2 });

        assertTrue(event.isError());
    }

    @Test
    public void omitsNeutralVolumeAndPitchRates() {
        RealtimeTtsPayload config = RealtimeTtsPayload.builder()
                .voice("zh_female_xxx")
                .format("pcm")
                .sampleRate(24000)
                .channels(1)
                .encoding("s16le")
                .volume(1.0)
                .pitch(1.0)
                .build();

        Map<String, Object> startPayload = payload(codec.startSessionFrame("session1", config));
        Map<String, Object> startReqParams = (Map<String, Object>) startPayload.get("req_params");
        Map<String, Object> audioParams = (Map<String, Object>) startReqParams.get("audio_params");

        assertFalse(audioParams.containsKey("loudness_rate"));
        assertFalse(startReqParams.containsKey("additions"));
        assertFalse(startReqParams.containsKey("vendor_options"));
    }

    @Test
    public void clampsPitchToHuoshanRange() {
        RealtimeTtsPayload config = RealtimeTtsPayload.builder()
                .voice("zh_female_xxx")
                .format("pcm")
                .sampleRate(24000)
                .channels(1)
                .encoding("s16le")
                .pitch(1.2)
                .build();

        Map<String, Object> startPayload = payload(codec.startSessionFrame("session1", config));
        Map<String, Object> startReqParams = (Map<String, Object>) startPayload.get("req_params");
        Map<String, Object> additions = additions(startReqParams);
        Map<String, Object> postProcess = (Map<String, Object>) additions.get("post_process");

        assertEquals(12, postProcess.get("pitch"));
    }

    @Test
    public void mapsBellaOpusToHuoshanOggOpus() {
        RealtimeTtsPayload config = RealtimeTtsPayload.builder()
                .voice("zh_female_xxx")
                .format("opus")
                .sampleRate(24000)
                .channels(1)
                .build();

        Map<String, Object> startPayload = payload(codec.startSessionFrame("session1", config));
        Map<String, Object> startReqParams = (Map<String, Object>) startPayload.get("req_params");
        Map<String, Object> audioParams = (Map<String, Object>) startReqParams.get("audio_params");

        assertEquals("ogg_opus", audioParams.get("format"));
    }

    @Test
    public void passesExtraBodyAdditionsToReqParams() {
        Map<String, Object> additions = new LinkedHashMap<>();
        additions.put("max_length_to_filter_parenthesis", 100);
        additions.put("disable_markdown_filter", true);
        Map<String, Object> extraBody = new LinkedHashMap<>();
        extraBody.put("additions", additions);
        RealtimeTtsPayload config = baseConfig().extraBody(extraBody).build();

        Map<String, Object> startPayload = payload(codec.startSessionFrame("session1", config));
        Map<String, Object> startReqParams = (Map<String, Object>) startPayload.get("req_params");
        Map<String, Object> actualAdditions = additions(startReqParams);

        assertEquals(100, actualAdditions.get("max_length_to_filter_parenthesis"));
        assertEquals(true, actualAdditions.get("disable_markdown_filter"));
        assertFalse(startReqParams.containsKey("vendor_options"));
    }

    @Test
    public void mergesExtraBodyAdditionsWithStandardPitchPriority() {
        Map<String, Object> postProcess = new LinkedHashMap<>();
        postProcess.put("pitch", -6);
        postProcess.put("style", "clear");
        Map<String, Object> additions = new LinkedHashMap<>();
        additions.put("post_process", postProcess);
        additions.put("disable_markdown_filter", true);
        Map<String, Object> extraBody = new LinkedHashMap<>();
        extraBody.put("additions", additions);
        RealtimeTtsPayload config = baseConfig()
                .pitch(1.2)
                .extraBody(extraBody)
                .build();

        Map<String, Object> startPayload = payload(codec.startSessionFrame("session1", config));
        Map<String, Object> startReqParams = (Map<String, Object>) startPayload.get("req_params");
        Map<String, Object> actualAdditions = additions(startReqParams);
        Map<String, Object> actualPostProcess = (Map<String, Object>) actualAdditions.get("post_process");

        assertEquals(12, actualPostProcess.get("pitch"));
        assertEquals("clear", actualPostProcess.get("style"));
        assertEquals(true, actualAdditions.get("disable_markdown_filter"));
    }

    @Test
    public void keepsNonConflictingExtraBodyFieldsOnReqParams() {
        Map<String, Object> extraBody = new LinkedHashMap<>();
        extraBody.put("context_language", "zh");
        extraBody.put("text", "should_not_send");
        extraBody.put("text_type", "ssml");
        extraBody.put("speaker", "should_not_override");
        extraBody.put("audio_params", singletonMap("format", "mp3"));
        RealtimeTtsPayload config = baseConfig().extraBody(extraBody).build();

        Map<String, Object> startPayload = payload(codec.startSessionFrame("session1", config));
        Map<String, Object> startReqParams = (Map<String, Object>) startPayload.get("req_params");
        Map<String, Object> audioParams = (Map<String, Object>) startReqParams.get("audio_params");

        assertEquals("zh_female_xxx", startReqParams.get("speaker"));
        assertEquals("plain", startReqParams.get("text_type"));
        assertEquals("pcm", audioParams.get("format"));
        assertEquals("zh", startReqParams.get("context_language"));
        assertFalse(startReqParams.containsKey("text"));
        assertFalse(startReqParams.containsKey("vendor_options"));
    }

    @Test
    public void doesNotMutateOriginalExtraBodyMap() {
        Map<String, Object> postProcess = new LinkedHashMap<>();
        postProcess.put("pitch", -6);
        Map<String, Object> additions = new LinkedHashMap<>();
        additions.put("post_process", postProcess);
        Map<String, Object> extraBody = new LinkedHashMap<>();
        extraBody.put("additions", additions);
        RealtimeTtsPayload config = baseConfig().pitch(1.2).extraBody(extraBody).build();

        payload(codec.startSessionFrame("session1", config));

        assertEquals(-6, ((Map<?, ?>) extraBodyMap(extraBody, "additions").get("post_process")).get("pitch"));
        assertNull(extraBody.get("post_process"));
    }

    @Test
    public void usesCurrentHuoshanSubtitleEventId() {
        assertEquals(364, HuoshanTtsWsCodec.EVENT_TTS_SUBTITLE);
    }

    private Map<String, Object> payload(ByteString frame) {
        byte[] bytes = frame.toByteArray();
        int offset = 4;
        offset += 4; // event
        int sessionSize = HuoshanTtsWsCodec.bytesToInt(bytes, offset);
        offset += 4 + sessionSize;
        int payloadSize = HuoshanTtsWsCodec.bytesToInt(bytes, offset);
        offset += 4;
        byte[] payload = new byte[payloadSize];
        System.arraycopy(bytes, offset, payload, 0, payload.length);
        return JacksonUtils.toMap(new String(payload, StandardCharsets.UTF_8));
    }

    private Map<String, Object> additions(Map<String, Object> reqParams) {
        Object additions = reqParams.get("additions");
        assertTrue(additions instanceof String);
        return JacksonUtils.toMap((String) additions);
    }

    private RealtimeTtsPayload.RealtimeTtsPayloadBuilder baseConfig() {
        return RealtimeTtsPayload.builder()
                .voice("zh_female_xxx")
                .format("pcm")
                .sampleRate(24000)
                .channels(1)
                .encoding("s16le")
                .textType("plain");
    }

    private Map<String, Object> singletonMap(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extraBodyMap(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }
}
