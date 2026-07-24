package com.ke.bella.openapi.protocol.tts.realtime.huoshan;

import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsPayload;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsTaskContext;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsTaskListener;
import com.ke.bella.openapi.protocol.tts.realtime.RealtimeTtsUpstreamConnection;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class HuoshanRealtimeTtsCallbackTest {
    @Test
    public void normalizesSubtitlePayloadToTimestampItems() {
        HuoshanTtsWsCodec codec = new HuoshanTtsWsCodec();
        TestListener listener = new TestListener();
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(codec, listener, new RealtimeTtsTaskContext());
        byte[] payload = ("{\"words\":[{\"word\":\"ni\",\"startTime\":0.12,\"endTime\":0.45,"
                + "\"beginIndex\":0,\"endIndex\":2,\"phoneme\":\"ni3\"}]}").getBytes(StandardCharsets.UTF_8);

        callback.onMessage(null, ByteString.of(serverFrame(HuoshanTtsWsCodec.EVENT_TTS_SUBTITLE, "session1", payload)));

        RealtimeTtsPayload.TimestampItem item = listener.timestamp.getItems().get(0);
        assertEquals("ni", item.getText());
        assertEquals(Long.valueOf(120), item.getStartMs());
        assertEquals(Long.valueOf(450), item.getEndMs());
        assertEquals(Integer.valueOf(0), item.getBeginIndex());
        assertEquals(Integer.valueOf(2), item.getEndIndex());
        assertEquals("ni3", item.getPhoneme());
        List<?> rawWords = (List<?>) listener.timestamp.getProviderMetadata().get("words");
        assertEquals("ni", ((Map<?, ?>) rawWords.get(0)).get("word"));
    }

    @Test
    public void providerCloseWithoutCancelIsFailed() {
        TestListener listener = new TestListener();
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(new HuoshanTtsWsCodec(), listener, new RealtimeTtsTaskContext());

        callback.onClosed(null, 1006, "abnormal close");

        assertEquals("provider_connection_closed", listener.failedCode);
        assertEquals("abnormal close", listener.providerMessage);
        assertNull(listener.cancelReason);
    }

    @Test
    public void providerCloseAfterCancelRequestIsCancelled() {
        TestListener listener = new TestListener();
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(new HuoshanTtsWsCodec(), listener, new RealtimeTtsTaskContext());

        callback.markCancelRequested();
        callback.onClosed(null, 1000, "cancelled");

        assertEquals("cancelled", listener.cancelReason);
        assertNull(listener.failedCode);
    }

    @Test
    public void startingReusedSessionClearsPreviousCancelState() {
        TestListener listener = new TestListener();
        RealtimeTtsTaskContext task = taskContext("session-b");
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(new HuoshanTtsWsCodec(), listener, task);

        callback.markCancelRequested();
        callback.markSessionStartSent(task);
        callback.onClosed(null, 1006, "abnormal close");

        assertEquals("provider_connection_closed", listener.failedCode);
        assertNull(listener.cancelReason);
    }

    @Test
    public void routesSessionEventsByProviderSessionId() {
        HuoshanTtsWsCodec codec = new HuoshanTtsWsCodec();
        TestListener listener = new TestListener();
        RealtimeTtsTaskContext taskA = taskContext("session-a");
        RealtimeTtsTaskContext taskB = taskContext("session-b");
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(codec, listener, taskA);
        RealtimeTtsUpstreamConnection connection = new RealtimeTtsUpstreamConnection(null, callback, null, 1L);
        callback.setConnection(connection);
        connection.register(taskA);
        connection.register(taskB);

        callback.onMessage(null, ByteString.of(serverFrame(HuoshanTtsWsCodec.EVENT_SESSION_STARTED, "session-b", "{}".getBytes(StandardCharsets.UTF_8))));
        callback.onMessage(null, ByteString.of(serverFrame(HuoshanTtsWsCodec.EVENT_SESSION_FINISHED, "session-b",
                "{\"usage\":{\"input_characters\":4}}".getBytes(StandardCharsets.UTF_8))));

        assertEquals(taskB, listener.startedTask);
        assertEquals(taskB, listener.completedTask);
        assertEquals(4, listener.providerUsage.get("input_characters"));
    }

    @Test
    public void connectionStartedStartsInitialSession() {
        HuoshanTtsWsCodec codec = new HuoshanTtsWsCodec();
        TestListener listener = new TestListener();
        RealtimeTtsTaskContext task = taskContext("session-a");
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(codec, listener, task);
        RealtimeTtsUpstreamConnection connection = new RealtimeTtsUpstreamConnection(null, callback, null, 1L);
        WebSocket webSocket = mock(WebSocket.class);
        when(webSocket.send(any(ByteString.class))).thenReturn(true);
        callback.setConnection(connection);
        connection.register(task);
        connection.setActiveProviderSessionId(task.getProviderSessionId());

        callback.onMessage(webSocket, ByteString.of(serverFrame(HuoshanTtsWsCodec.EVENT_CONNECTION_STARTED, "connection-a", new byte[0])));

        assertTrue(connection.isConnectionStarted());
        assertNotNull(task.getUpstreamSessionStartSentMillis());
        verify(webSocket).send(any(ByteString.class));
    }

    @Test
    public void ignoresUnknownProviderSessionEvent() {
        HuoshanTtsWsCodec codec = new HuoshanTtsWsCodec();
        TestListener listener = new TestListener();
        RealtimeTtsTaskContext taskA = taskContext("session-a");
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(codec, listener, taskA);
        RealtimeTtsUpstreamConnection connection = new RealtimeTtsUpstreamConnection(null, callback, null, 1L);
        callback.setConnection(connection);
        connection.register(taskA);

        callback.onMessage(null, ByteString.of(serverFrame(HuoshanTtsWsCodec.EVENT_SESSION_STARTED, "missing-session", "{}".getBytes(StandardCharsets.UTF_8))));

        assertNull(listener.startedTask);
    }

    @Test
    public void routesSessionFailedByProviderSessionIdEvenWhenMarkedError() {
        HuoshanTtsWsCodec codec = new HuoshanTtsWsCodec();
        TestListener listener = new TestListener();
        RealtimeTtsTaskContext taskA = taskContext("session-a");
        RealtimeTtsTaskContext taskB = taskContext("session-b");
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(codec, listener, taskA);
        RealtimeTtsUpstreamConnection connection = new RealtimeTtsUpstreamConnection(null, callback, null, 1L);
        callback.setConnection(connection);
        connection.register(taskA);
        connection.register(taskB);
        connection.setActiveProviderSessionId("session-b");

        callback.onMessage(null, ByteString.of(serverFrame(HuoshanTtsWsCodec.EVENT_SESSION_FAILED, "session-a",
                "{\"status_code\":\"BadRequest\",\"message\":\"bad old session\"}".getBytes(StandardCharsets.UTF_8))));

        assertEquals(taskA, listener.failedTask);
        assertTrue(taskA.isProviderSessionFailure());
        assertFalse(taskB.isProviderSessionFailure());
        assertEquals("BadRequest", listener.providerCode);
        assertEquals("bad old session", listener.providerMessage);
    }

    @Test
    public void ignoresUnknownSessionFailedWithoutFailingActiveTask() {
        HuoshanTtsWsCodec codec = new HuoshanTtsWsCodec();
        TestListener listener = new TestListener();
        RealtimeTtsTaskContext taskB = taskContext("session-b");
        HuoshanRealtimeTtsCallback callback = new HuoshanRealtimeTtsCallback(codec, listener, taskB);
        RealtimeTtsUpstreamConnection connection = new RealtimeTtsUpstreamConnection(null, callback, null, 1L);
        callback.setConnection(connection);
        connection.register(taskB);
        connection.setActiveProviderSessionId("session-b");

        callback.onMessage(null, ByteString.of(serverFrame(HuoshanTtsWsCodec.EVENT_SESSION_FAILED, "missing-session",
                "{\"status_code\":\"BadRequest\",\"message\":\"unknown session\"}".getBytes(StandardCharsets.UTF_8))));

        assertNull(listener.failedTask);
        assertNull(listener.failedCode);
    }

    private static RealtimeTtsTaskContext taskContext(String providerSessionId) {
        RealtimeTtsTaskContext taskContext = new RealtimeTtsTaskContext();
        taskContext.setProviderSessionId(providerSessionId);
        taskContext.setFrozenPayload(RealtimeTtsPayload.builder()
                .format("pcm")
                .sampleRate(24000)
                .channels(1)
                .encoding("s16le")
                .build());
        return taskContext;
    }

    private static byte[] serverFrame(int event, String sessionId, byte[] payload) {
        byte[] sessionBytes = sessionId.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, new byte[] {
                (byte) ((HuoshanTtsWsCodec.PROTOCOL_VERSION << 4) | HuoshanTtsWsCodec.DEFAULT_HEADER_SIZE),
                (byte) ((HuoshanTtsWsCodec.FULL_SERVER_RESPONSE << 4) | HuoshanTtsWsCodec.MSG_TYPE_FLAG_WITH_EVENT),
                (byte) ((HuoshanTtsWsCodec.JSON << 4) | HuoshanTtsWsCodec.COMPRESSION_NO),
                0
        });
        write(out, HuoshanTtsWsCodec.intToBytes(event));
        write(out, HuoshanTtsWsCodec.intToBytes(sessionBytes.length));
        write(out, sessionBytes);
        write(out, HuoshanTtsWsCodec.intToBytes(payload.length));
        write(out, payload);
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    private static class TestListener implements RealtimeTtsTaskListener {
        RealtimeTtsPayload timestamp;
        RealtimeTtsTaskContext startedTask;
        RealtimeTtsTaskContext completedTask;
        RealtimeTtsTaskContext failedTask;
        Map<String, Object> providerUsage;
        String cancelReason;
        String failedCode;
        String providerCode;
        String providerMessage;

        @Override
        public void onSpeechStarted(RealtimeTtsTaskContext taskContext) {
            startedTask = taskContext;
        }

        @Override
        public void onAudio(RealtimeTtsTaskContext taskContext, byte[] audio, Long durationMs) {
        }

        @Override
        public void onTimestamp(RealtimeTtsTaskContext taskContext, RealtimeTtsPayload payload) {
            timestamp = payload;
        }

        @Override
        public void onCompleted(RealtimeTtsTaskContext taskContext, Map<String, Object> providerUsage) {
            completedTask = taskContext;
            this.providerUsage = providerUsage;
        }

        @Override
        public void onCancelled(RealtimeTtsTaskContext taskContext, String reason) {
            cancelReason = reason;
        }

        @Override
        public void onFailed(RealtimeTtsTaskContext taskContext, String code, String message, String providerCode, String providerMessage) {
            failedTask = taskContext;
            failedCode = code;
            this.providerCode = providerCode;
            this.providerMessage = providerMessage;
        }
    }
}
