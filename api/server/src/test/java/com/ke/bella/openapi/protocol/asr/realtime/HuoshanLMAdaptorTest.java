package com.ke.bella.openapi.protocol.asr.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import com.ke.bella.openapi.protocol.asr.HuoshanLMRealTimeAsrResponse;
import com.ke.bella.openapi.protocol.asr.SpeechContext;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import com.ke.bella.openapi.utils.JacksonUtils;

public class HuoshanLMAdaptorTest {

    @Test
    public void rejectsHotWordsAndSpeechContextBeforeOpeningUpstreamConnection() {
        RealTimeMessage request = new RealTimeMessage();
        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
        payload.setHotWords("word");
        payload.setSpeechContext(new SpeechContext());
        request.setPayload(payload);

        try {
            HuoshanLMAdaptor.validateRequest(request);
            fail("conflicting context should fail before opening the upstream connection");
        } catch (IllegalArgumentException e) {
            assertEquals("hot_words and speech_context cannot be used together", e.getMessage());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void converterPassesUtteranceAdditionsToRealtimePayload() throws Exception {
        String responseJson = "{"
                + "\"audio_info\":{\"duration\":1200},"
                + "\"result\":{\"utterances\":[{"
                + "\"text\":\"hello\","
                + "\"start_time\":100,"
                + "\"end_time\":900,"
                + "\"definite\":true,"
                + "\"confidence\":0.98,"
                + "\"additions\":{\"gender\":\"female\",\"speaker\":\"1\"}"
                + "}]}"
                + "}";
        HuoshanLMRealTimeAsrResponse response = JacksonUtils.deserialize(responseJson, HuoshanLMRealTimeAsrResponse.class);

        List<String> events = converter("task-1").apply(response);

        Map<String, Object> sentenceEnd = JacksonUtils.toMap(events.get(1));
        Map<String, Object> payload = (Map<String, Object>) sentenceEnd.get("payload");
        Map<String, Object> additions = (Map<String, Object>) payload.get("additions");
        assertEquals("female", additions.get("gender"));
        assertEquals("1", additions.get("speaker"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void converterMapsAbsoluteSentenceTimesAndPreservesZeroStart() throws Exception {
        String responseJson = "{\"result\":{\"utterances\":[{"
                + "\"text\":\"hello\",\"start_time\":0,\"end_time\":1705,"
                + "\"definite\":true,\"confidence\":0.98,\"words\":[{\"text\":\"hello\","
                + "\"start_time\":0,\"end_time\":1705,\"confidence\":0.9}]"
                + "}]}}";

        List<String> events = converter("task-1").apply(
                JacksonUtils.deserialize(responseJson, HuoshanLMRealTimeAsrResponse.class));
        Map<String, Object> payload = (Map<String, Object>) JacksonUtils.toMap(events.get(1)).get("payload");

        assertEquals(0, payload.get("begin_time"));
        assertEquals(1705, payload.get("time"));
        assertNull(payload.get("end_time"));
        assertEquals("hello", payload.get("result"));
        assertEquals(1, ((List<?>) payload.get("words")).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void converterMapsNonZeroSentenceTimesToAbsoluteEndTime() throws Exception {
        String firstResponse = "{\"result\":{\"utterances\":[{"
                + "\"text\":\"first\",\"start_time\":0,\"end_time\":1705,\"definite\":true"
                + "}]}}";
        String secondResponse = "{\"result\":{\"utterances\":[{"
                + "\"text\":\"second\",\"start_time\":2110,\"end_time\":3696,\"definite\":true"
                + "}]}}";

        Function<HuoshanLMRealTimeAsrResponse, List<String>> converter = converter("task-1");
        converter.apply(JacksonUtils.deserialize(firstResponse, HuoshanLMRealTimeAsrResponse.class));
        List<String> events = converter.apply(
                JacksonUtils.deserialize(secondResponse, HuoshanLMRealTimeAsrResponse.class));
        Map<String, Object> payload = (Map<String, Object>) JacksonUtils.toMap(events.get(1)).get("payload");

        assertEquals(2110, payload.get("begin_time"));
        assertEquals(3696, payload.get("time"));
        assertNull(payload.get("end_time"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void converterLeavesInvalidSentenceTimesNull() throws Exception {
        String responseJson = "{\"result\":{\"utterances\":["
                + "{\"text\":\"missing-start\",\"end_time\":900,\"definite\":true},"
                + "{\"text\":\"missing-end\",\"start_time\":100,\"definite\":true},"
                + "{\"text\":\"reversed\",\"start_time\":900,\"end_time\":100,\"definite\":true}"
                + "]}}";

        List<String> events = converter("task-1").apply(
                JacksonUtils.deserialize(responseJson, HuoshanLMRealTimeAsrResponse.class));
        for (int eventIndex = 1; eventIndex < events.size(); eventIndex++) {
            Map<String, Object> payload = (Map<String, Object>) JacksonUtils.toMap(events.get(eventIndex)).get("payload");
            assertNull(payload.get("begin_time"));
            assertNull(payload.get("time"));
            assertNull(payload.get("end_time"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void converterDoesNotInventTimesForTextOnlyResult() throws Exception {
        HuoshanLMRealTimeAsrResponse response = JacksonUtils.deserialize(
                "{\"audio_info\":{\"duration\":1200},\"result\":{\"text\":\"hello\"}}",
                HuoshanLMRealTimeAsrResponse.class);

        List<String> events = converter("task-1").apply(response);
        Map<String, Object> payload = (Map<String, Object>) JacksonUtils.toMap(events.get(1)).get("payload");

        assertEquals(1200, payload.get("time"));
        assertNull(payload.get("begin_time"));
        assertNull(payload.get("end_time"));
    }

    @SuppressWarnings("unchecked")
    private Function<HuoshanLMRealTimeAsrResponse, List<String>> converter(String taskId) throws Exception {
        Class<?> converterClass = Class.forName("com.ke.bella.openapi.protocol.asr.realtime.HuoshanLMAdaptor$Converter");
        Constructor<?> constructor = converterClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return (Function<HuoshanLMRealTimeAsrResponse, List<String>>) constructor.newInstance(taskId);
    }
}
