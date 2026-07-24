package com.ke.bella.openapi.protocol.asr.realtime;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import com.ke.bella.openapi.protocol.asr.HuoshanLMRealTimeAsrResponse;
import com.ke.bella.openapi.utils.JacksonUtils;

public class HuoshanLMAdaptorTest {

    @Test
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

    @SuppressWarnings("unchecked")
    private Function<HuoshanLMRealTimeAsrResponse, List<String>> converter(String taskId) throws Exception {
        Class<?> converterClass = Class.forName("com.ke.bella.openapi.protocol.asr.realtime.HuoshanLMAdaptor$Converter");
        Constructor<?> constructor = converterClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return (Function<HuoshanLMRealTimeAsrResponse, List<String>>) constructor.newInstance(taskId);
    }
}
