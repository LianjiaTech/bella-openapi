package com.ke.bella.openapi.protocol.asr.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.EOFException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.TencentRealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.TencentRealTimeAsrResponse;
import com.ke.bella.openapi.protocol.asr.TencentStreamAsrCallback;
import com.ke.bella.openapi.protocol.realtime.RealTimeEventType;
import com.ke.bella.openapi.utils.JacksonUtils;

public class TencentAdaptorTest {

    @Test
    public void responseMapsFinalJsonField() {
        TencentRealTimeAsrResponse response = JacksonUtils.deserialize(
                "{\"code\":0,\"message\":\"success\",\"final\":1}", TencentRealTimeAsrResponse.class);

        assertTrue(response.isFinal());
    }

    @Test
    public void callbackHandlesHandshakeSeparatelyFromFinalMessage() throws Exception {
        TestSender sender = new TestSender();
        EndpointProcessData processData = new EndpointProcessData();
        TencentStreamAsrCallback callback = callback(sender, processData);

        callback.onMessage(null, "{\"code\":0,\"message\":\"success\"}");

        assertTrue(callback.started());
        assertEquals(0, sender.texts.size());
        assertFalse(sender.closed);

        callback.onMessage(null, "{\"code\":0,\"message\":\"success\",\"final\":1}");

        assertEquals(1, sender.texts.size());
        assertEquals(RealTimeEventType.TRANSCRIPTION_COMPLETED.getValue(),
                header(JacksonUtils.toMap(sender.texts.get(0))).get("name"));
        assertTrue(sender.closed);
        assertFalse(processData.getMetrics().containsKey("ttft"));
    }

    @Test
    public void callbackIgnoresEofOnlyAfterFinalMessage() throws Exception {
        TestSender beforeFinalSender = new TestSender();
        TencentStreamAsrCallback beforeFinal = callback(beforeFinalSender, new EndpointProcessData());

        beforeFinal.onFailure(null, new EOFException(), null);

        assertEquals(1, beforeFinalSender.errors.size());

        TestSender afterFinalSender = new TestSender();
        TencentStreamAsrCallback afterFinal = callback(afterFinalSender, new EndpointProcessData());
        afterFinal.onMessage(null, "{\"code\":0,\"message\":\"success\",\"final\":1}");

        afterFinal.onFailure(null, new EOFException(), null);

        assertEquals(0, afterFinalSender.errors.size());
    }

    @Test
    public void callbackRecordsTtftForActualResult() throws Exception {
        EndpointProcessData processData = new EndpointProcessData();
        TencentStreamAsrCallback callback = callback(new TestSender(), processData);

        callback.onMessage(null,
                "{\"code\":0,\"message\":\"success\",\"result\":{\"slice_type\":1,\"voice_text_str\":\"test\"}}");

        assertTrue(processData.getMetrics().containsKey("ttft"));
    }

    private TencentStreamAsrCallback callback(TestSender sender, EndpointProcessData processData) throws Exception {
        return new TencentStreamAsrCallback(
                mock(TencentRealTimeAsrRequest.class),
                sender,
                processData,
                null,
                converter("task-1"));
    }

    @SuppressWarnings("unchecked")
    private Function<TencentRealTimeAsrResponse, List<String>> converter(String taskId) throws Exception {
        Class<?> converterClass = Class.forName("com.ke.bella.openapi.protocol.asr.realtime.TencentAdaptor$Converter");
        Constructor<?> constructor = converterClass.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return (Function<TencentRealTimeAsrResponse, List<String>>) constructor.newInstance(taskId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> header(Map<String, Object> event) {
        return (Map<String, Object>) event.get("header");
    }

    private static class TestSender implements Callbacks.Sender {
        private final List<String> texts = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();
        private boolean closed;

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
            closed = true;
        }
    }
}
