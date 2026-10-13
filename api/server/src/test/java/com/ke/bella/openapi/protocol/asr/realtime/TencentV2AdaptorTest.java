package com.ke.bella.openapi.protocol.asr.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.EOFException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.TencentV2Property;
import com.ke.bella.openapi.protocol.asr.TencentV2RealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.TencentV2RealTimeAsrResponse;
import com.ke.bella.openapi.protocol.asr.TencentV2StreamAsrCallback;
import com.ke.bella.openapi.protocol.realtime.RealTimeEventType;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import com.ke.bella.openapi.utils.JacksonUtils;

import java.lang.reflect.Constructor;

import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;

public class TencentV2AdaptorTest {

    @Test
    public void requestMapsBellaPayloadToTencentV2Params() {
        TencentV2Property property = property();
        RealTimeMessage request = startRequest("wav", 8000, true);

        TencentV2RealTimeAsrRequest tencentRequest = new TencentV2RealTimeAsrRequest(request, property);

        assertEquals("16k_zh_en_2.0", tencentRequest.getEngineModelType());
        assertEquals(Integer.valueOf(12), tencentRequest.getVoiceFormat());
        assertEquals(null, tencentRequest.getInputSampleRate());
        assertEquals("腾讯云|10", tencentRequest.getHotwordList());
        assertEquals("hotword-id", tencentRequest.getHotwordId());
        assertEquals("custom-id", tencentRequest.getCustomizationId());
        assertEquals("replace-id", tencentRequest.getReplaceTextId());
        assertEquals(Integer.valueOf(1), tencentRequest.getNeedvad());
        assertEquals(Integer.valueOf(1), tencentRequest.getFilterDirty());
        assertEquals(Integer.valueOf(0), tencentRequest.getFilterModal());
        assertEquals(Integer.valueOf(1), tencentRequest.getFilterEmptyResult());
        assertEquals(Integer.valueOf(2), tencentRequest.getConvertNumMode());
        assertEquals(Float.valueOf(0.8f), tencentRequest.getNoiseThreshold());
        assertEquals(Integer.valueOf(1), tencentRequest.getSentenceStrategy());
        assertEquals(Integer.valueOf(2), tencentRequest.getDomain());
        assertEquals(Integer.valueOf(1), tencentRequest.getEnableSpeakerContext());
        assertEquals("speaker-context-id", tencentRequest.getSpeakerContextId());
    }

    @Test
    public void requestSetsInputSampleRateOnlyForPcm8k() {
        TencentV2RealTimeAsrRequest tencentRequest = new TencentV2RealTimeAsrRequest(startRequest("pcm", 8000, false), property());

        assertEquals(Integer.valueOf(1), tencentRequest.getVoiceFormat());
        assertEquals(Integer.valueOf(8000), tencentRequest.getInputSampleRate());
    }

    @Test
    public void requestRequiresConfiguredEngineModelType() {
        TencentV2Property property = new TencentV2Property();

        try {
            new TencentV2RealTimeAsrRequest(startRequest("pcm", 16000, true), property);
            fail("engineModelType should be required");
        } catch (BizParamCheckException e) {
            assertTrue(e.getMessage().contains("engineModelType"));
        }
    }

    @Test
    public void urlContainsTencentV2ParamsAndEncodedSignature() {
        TencentV2Property property = property();
        TencentV2RealTimeAsrRequest tencentRequest = new TencentV2RealTimeAsrRequest(startRequest("opus", 16000, false), property);
        TencentV2Adaptor adaptor = new TencentV2Adaptor();

        String url = adaptor.buildWebSocketUrl("wss://asr.cloud.tencent.com/asr/v2/", property, tencentRequest);
        Map<String, String> query = query(url);

        assertTrue(url.startsWith("wss://asr.cloud.tencent.com/asr/v2/1250000000?"));
        assertEquals("10", query.get("voice_format"));
        assertEquals("16k_zh_en_2.0", query.get("engine_model_type"));
        assertEquals("1", query.get("needvad"));
        assertEquals("1", query.get("filter_dirty"));
        assertEquals("0", query.get("filter_modal"));
        assertEquals("1", query.get("filter_empty_result"));
        assertEquals("2", query.get("convert_num_mode"));
        assertEquals("0.8", query.get("noise_threshold"));
        assertEquals("1", query.get("sentence_strategy"));
        assertEquals("2", query.get("domain"));
        assertEquals("1", query.get("enable_speaker_context"));
        assertEquals("speaker-context-id", query.get("speaker_context_id"));
        assertEquals("secret-id", query.get("secretid"));
        assertTrue(query.containsKey("signature"));
    }

    @Test
    public void converterEmitsBellaEventsWithSpeakerAdditions() throws Exception {
        TencentV2RealTimeAsrResponse response = JacksonUtils.deserialize("{"
                + "\"code\":0,"
                + "\"message\":\"success\","
                + "\"voice_id\":\"voice-1\","
                + "\"message_id\":\"msg-1\","
                + "\"sentences\":{"
                + "\"sentence_list\":[{"
                + "\"sentence\":\"实时语音识别。\","
                + "\"sentence_type\":1,"
                + "\"sentence_id\":3,"
                + "\"speaker_id\":2,"
                + "\"start_time\":1200,"
                + "\"end_time\":2850"
                + "}]}}", TencentV2RealTimeAsrResponse.class);

        List<String> events = converter("task-1").apply(response);

        assertEquals(2, events.size());
        Map<String, Object> begin = JacksonUtils.toMap(events.get(0));
        Map<String, Object> end = JacksonUtils.toMap(events.get(1));
        assertEquals(RealTimeEventType.SENTENCE_BEGIN.getValue(), header(begin).get("name"));
        assertEquals(RealTimeEventType.SENTENCE_END.getValue(), header(end).get("name"));
        Map<String, Object> payload = payload(end);
        Map<String, Object> additions = (Map<String, Object>) payload.get("additions");
        assertEquals("实时语音识别。", payload.get("result"));
        assertEquals(1200, payload.get("begin_time"));
        assertEquals(2850, payload.get("time"));
        assertNull(payload.get("end_time"));
        assertEquals(2, additions.get("speaker_id"));
        assertEquals("voice-1", additions.get("voice_id"));
        assertEquals("msg-1", additions.get("message_id"));
    }

    @Test
    public void converterHandlesMultipleTencentV2Sentences() throws Exception {
        TencentV2RealTimeAsrResponse response = JacksonUtils.deserialize("{"
                + "\"code\":0,"
                + "\"message\":\"success\","
                + "\"voice_id\":\"voice-1\","
                + "\"message_id\":\"msg-2\","
                + "\"sentences\":{"
                + "\"sentence_list\":["
                + "{\"sentence\":\"第一句。\",\"sentence_type\":1,\"sentence_id\":1,\"speaker_id\":0,\"start_time\":100,\"end_time\":900},"
                + "{\"sentence\":\"第二句\",\"sentence_type\":0,\"sentence_id\":2,\"speaker_id\":-1,\"start_time\":900,\"end_time\":1500}"
                + "]}}", TencentV2RealTimeAsrResponse.class);

        List<String> events = converter("task-1").apply(response);

        assertEquals(4, events.size());
        assertEquals(RealTimeEventType.SENTENCE_BEGIN.getValue(), header(JacksonUtils.toMap(events.get(0))).get("name"));
        assertEquals(RealTimeEventType.SENTENCE_END.getValue(), header(JacksonUtils.toMap(events.get(1))).get("name"));
        assertEquals(RealTimeEventType.SENTENCE_BEGIN.getValue(), header(JacksonUtils.toMap(events.get(2))).get("name"));
        assertEquals(RealTimeEventType.TRANSCRIPTION_RESULT_CHANGED.getValue(), header(JacksonUtils.toMap(events.get(3))).get("name"));
        assertEquals("第二句", payload(JacksonUtils.toMap(events.get(3))).get("result"));
    }

    @Test
    public void responseReadsFinalField() {
        TencentV2RealTimeAsrResponse response = JacksonUtils.deserialize("{\"code\":0,\"message\":\"success\",\"final\":1}",
                TencentV2RealTimeAsrResponse.class);

        assertTrue(response.isFinal());
    }

    @Test
    public void callbackFailsStartedImmediatelyWhenHandshakeFails() {
        TencentV2StreamAsrCallback callback = new TencentV2StreamAsrCallback(
                new TencentV2RealTimeAsrRequest(startRequest("pcm", 16000, false), property()),
                new TestSender(),
                new EndpointProcessData(),
                null,
                converter -> java.util.Collections.emptyList());

        callback.onMessage(null, "{\"code\":4002,\"message\":\"鉴权失败\"}");

        try {
            callback.started();
            fail("started should fail when handshake fails");
        } catch (BellaException e) {
            assertTrue(e.getMessage().contains("鉴权失败"));
        }
    }

    @Test
    public void callbackIgnoresEofAfterFinalMessage() {
        TestSender sender = new TestSender();
        TencentV2StreamAsrCallback callback = new TencentV2StreamAsrCallback(
                new TencentV2RealTimeAsrRequest(startRequest("pcm", 16000, false), property()),
                sender,
                new EndpointProcessData(),
                null,
                converter -> java.util.Collections.emptyList());

        callback.onMessage(null, "{\"code\":0,\"message\":\"success\",\"final\":1}");
        callback.onFailure(null, new EOFException(), null);

        assertEquals(0, sender.errors.size());
    }

    @Test
    public void callbackCapturesSpeakerContextIdFromHandshake() {
        TencentV2StreamAsrCallback callback = new TencentV2StreamAsrCallback(
                new TencentV2RealTimeAsrRequest(startRequest("pcm", 16000, false), property()),
                new TestSender(),
                new EndpointProcessData(),
                null,
                converter -> java.util.Collections.emptyList());

        callback.onMessage(null, "{\"code\":0,\"message\":\"success\",\"voice_id\":\"voice-1\",\"speaker_context_id\":\"ctx-1\"}");

        assertEquals("ctx-1", callback.getSpeakerContextId());
    }

    private TencentV2Property property() {
        TencentV2Property property = new TencentV2Property();
        property.setAppid("1250000000");
        property.setAuth(AuthorizationProperty.builder().apiKey("secret-id").secret("secret-key").build());
        property.setEngineModelType("16k_zh_en_2.0");
        return property;
    }

    private RealTimeMessage startRequest(String format, int sampleRate, boolean speaker) {
        RealTimeMessage request = new RealTimeMessage();
        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
        payload.setFormat(format);
        payload.setSampleRate(sampleRate);
        payload.setEnableSpeakerInfo(speaker);
        payload.setHotWords("腾讯云|10");
        payload.setHotWordsTableId("hotword-id");
        payload.setCustomizationId("custom-id");
        payload.setVocabularyId("replace-id");
        payload.setSpeechNoiseThreshold(0.8f);
        Map<String, Object> additions = new HashMap<>();
        additions.put("needvad", 1);
        additions.put("filter_dirty", 1);
        additions.put("filter_modal", 0);
        additions.put("filter_empty_result", 1);
        additions.put("convert_num_mode", 2);
        additions.put("sentence_strategy", 1);
        additions.put("domain", 2);
        additions.put("enable_speaker_context", 1);
        additions.put("speaker_context_id", "speaker-context-id");
        payload.setAdditions(additions);
        request.setPayload(payload);
        return request;
    }

    private Map<String, String> query(String url) {
        return Stream.of(url.substring(url.indexOf('?') + 1).split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> decode(pair[1])));
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Function<TencentV2RealTimeAsrResponse, List<String>> converter(String taskId) throws Exception {
        Class<?> converterClass = Class.forName("com.ke.bella.openapi.protocol.asr.realtime.TencentV2Adaptor$Converter");
        Constructor<?> constructor = converterClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return (Function<TencentV2RealTimeAsrResponse, List<String>>) constructor.newInstance(taskId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> header(Map<String, Object> event) {
        return (Map<String, Object>) event.get("header");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(Map<String, Object> event) {
        return (Map<String, Object>) event.get("payload");
    }

    private static class TestSender implements Callbacks.Sender {
        private final List<String> texts = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();

        @Override
        public void send(String text) {
            texts.add(text);
        }

        @Override
        public void send(byte[] bytes) {
        }

        @Override
        public void onError(Throwable e) {
            errors.add(e);
        }

        @Override
        public void close() {
        }
    }
}
