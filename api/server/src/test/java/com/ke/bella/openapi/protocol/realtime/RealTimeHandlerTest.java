package com.ke.bella.openapi.protocol.realtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.service.CredentialSession;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.WebSocket;

public class RealTimeHandlerTest {

    @Test
    public void durationMillisUsesAbsoluteIntervalWhenAvailable() {
        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
        payload.setBeginTime(2110);
        payload.setTime(3696);

        assertEquals(1586L, RealTimeHandler.durationMillis(payload));
    }

    @Test
    public void durationMillisUsesTimeAsFallback() {
        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
        payload.setTime(1200);

        assertEquals(1200L, RealTimeHandler.durationMillis(payload));
    }

    @Test
    public void durationMillisFallsBackToTimeWhenAbsoluteIntervalIsUnavailable() {
        RealTimeMessage.Payload missingBegin = new RealTimeMessage.Payload();
        missingBegin.setTime(900);
        RealTimeMessage.Payload missingEnd = new RealTimeMessage.Payload();
        missingEnd.setBeginTime(100);
        RealTimeMessage.Payload reversed = new RealTimeMessage.Payload();
        reversed.setBeginTime(900);
        reversed.setTime(100);
        RealTimeMessage.Payload unavailable = new RealTimeMessage.Payload();

        assertEquals(900L, RealTimeHandler.durationMillis(missingBegin));
        assertEquals(0L, RealTimeHandler.durationMillis(missingEnd));
        assertEquals(0L, RealTimeHandler.durationMillis(reversed));
        assertEquals(0L, RealTimeHandler.durationMillis(unavailable));
    }

    @Test
    public void durationMillisUsesTimeAsAbsoluteEndPointWhenBeginIsPresent() {
        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
        payload.setBeginTime(100);
        payload.setTime(1200);

        assertEquals(1100L, RealTimeHandler.durationMillis(payload));
    }

    @Test
    public void accumulatorUsesExactMillisecondsBeforeRounding() {
        RealTimeMessage.Payload first = new RealTimeMessage.Payload();
        first.setBeginTime(722);
        first.setTime(7742);
        RealTimeMessage.Payload second = new RealTimeMessage.Payload();
        second.setBeginTime(30242);
        second.setTime(35902);
        RealTimeMessage.Payload third = new RealTimeMessage.Payload();
        third.setBeginTime(60822);
        third.setTime(89502);

        RealTimeHandler.TranscriptionDurationAccumulator accumulator = new RealTimeHandler.TranscriptionDurationAccumulator();
        accumulator.record(first);
        accumulator.record(second);
        accumulator.record(third);

        assertEquals(42, accumulator.seconds());
    }

    @Test
    public void taskDurationFallbackUsesLatestDurationOnlyWhenAbsoluteTimesAreUnavailable() {
        RealTimeMessage.Payload first = new RealTimeMessage.Payload();
        first.setTime(1200);
        RealTimeMessage.Payload latest = new RealTimeMessage.Payload();
        latest.setTime(2400);

        RealTimeHandler.TranscriptionDurationAccumulator fallback = new RealTimeHandler.TranscriptionDurationAccumulator();
        fallback.record(first);
        fallback.record(latest);

        assertEquals(3, fallback.seconds());
    }

    @Test
    public void absoluteSentenceTimesTakePrecedenceOverTaskDurationFallback() {
        RealTimeMessage.Payload fallback = new RealTimeMessage.Payload();
        fallback.setTime(2400);
        RealTimeMessage.Payload sentence = new RealTimeMessage.Payload();
        sentence.setBeginTime(3000);
        sentence.setTime(4200);

        RealTimeHandler.TranscriptionDurationAccumulator accumulator = new RealTimeHandler.TranscriptionDurationAccumulator();
        accumulator.record(fallback);
        accumulator.record(sentence);

        assertEquals(2, accumulator.seconds());
    }


    @Test
    public void invalidMessageClosesClientSession() throws Exception {
        RealTimeAdaptor<AsrProperty> adaptor = mock(RealTimeAdaptor.class);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/asr/stream");
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        RealTimeHandler handler = new RealTimeHandler("wss://upstream", new AsrProperty(), processData, null, adaptor,
                CredentialSession.NOOP, null);

        handler.handleTextMessage(session, new TextMessage("{}"));

        verify(session).sendMessage(any(TextMessage.class));
        verify(session).close();
    }

    @Test
    public void senderErrorClosesClientAndUpstream() throws Exception {
        RealTimeAdaptor<AsrProperty> adaptor = mock(RealTimeAdaptor.class);
        Callbacks.WebSocketCallback callback = mock(Callbacks.WebSocketCallback.class);
        WebSocket upstream = mock(WebSocket.class);
        ArgumentCaptor<Callbacks.Sender> senderCaptor = ArgumentCaptor.forClass(Callbacks.Sender.class);
        when(adaptor.createCallback(senderCaptor.capture(), any(), any(), anyString(), any(), any())).thenReturn(callback);
        when(adaptor.startTranscription(anyString(), any(), any(), any())).thenReturn(upstream);

        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/asr/stream");
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        RealTimeHandler handler = new RealTimeHandler("wss://upstream", new AsrProperty(), processData, null, adaptor,
                CredentialSession.NOOP, null);

        handler.handleTextMessage(session, new TextMessage("{\"header\":{\"name\":\"StartTranscription\"}}"));
        senderCaptor.getValue().onError(new IllegalStateException("upstream failed"));

        verify(adaptor).closeConnection(upstream);
        verify(session, times(2)).sendMessage(any(TextMessage.class));
        verify(session).close();
    }

    @Test
    public void failedStopClosesClientAndUpstream() throws Exception {
        RealTimeAdaptor<AsrProperty> adaptor = mock(RealTimeAdaptor.class);
        Callbacks.WebSocketCallback callback = mock(Callbacks.WebSocketCallback.class);
        WebSocket upstream = mock(WebSocket.class);
        when(adaptor.createCallback(any(), any(), any(), anyString(), any(), any())).thenReturn(callback);
        when(adaptor.startTranscription(anyString(), any(), any(), any())).thenReturn(upstream);
        when(adaptor.stopTranscription(any(), any(), any())).thenReturn(false);

        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/asr/stream");
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        RealTimeHandler handler = new RealTimeHandler("wss://upstream", new AsrProperty(), processData, null, adaptor,
                CredentialSession.NOOP, null);

        handler.handleTextMessage(session, new TextMessage("{\"header\":{\"name\":\"StartTranscription\"}}"));
        handler.handleTextMessage(session, new TextMessage("{\"header\":{\"name\":\"StopTranscription\"}}"));

        verify(adaptor).closeConnection(upstream);
        verify(session, times(2)).sendMessage(any(TextMessage.class));
        verify(session).close();
    }

    @Test
    public void startedResponseIncludesHandshakeTraceFields() {
        Callbacks.WebSocketCallback callback = new Callbacks.WebSocketCallback() {
            @Override public String getUpstreamLogId() { return "log-123"; }
            @Override public String getUpstreamConnectId() { return "connect-456"; }
            @Override public void onOpen(okhttp3.WebSocket webSocket, okhttp3.Response response) { }
            @Override public void onMessage(okhttp3.WebSocket webSocket, okio.ByteString bytes) { }
            @Override public void onMessage(okhttp3.WebSocket webSocket, String text) { }
            @Override public void onClosing(okhttp3.WebSocket webSocket, int code, String reason) { }
            @Override public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) { }
            @Override public void onFailure(okhttp3.WebSocket webSocket, Throwable t, okhttp3.Response response) { }
            @Override public boolean started() { return true; }
        };

        RealTimeMessage response = RealTimeHandler.startedResponse("task-123", callback);

        assertEquals("log-123", response.getPayload().getAdditions().get("logid"));
        assertEquals("connect-456", response.getPayload().getAdditions().get("connect_id"));
    }

    @Test
    public void startedResponseKeepsPayloadAbsentWithoutHandshakeTrace() {
        assertNull(RealTimeHandler.startedResponse("task-123", null).getPayload());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void forwardsSpeechContextWithoutGatewayValidation() throws Exception {
        RealTimeAdaptor<AsrProperty> adaptor = mock(RealTimeAdaptor.class);
        Callbacks.WebSocketCallback callback = mock(Callbacks.WebSocketCallback.class);
        when(adaptor.createCallback(any(), any(), any(), anyString(), any(), any())).thenReturn(callback);
        when(adaptor.startTranscription(anyString(), any(), any(), any())).thenReturn(mock(WebSocket.class));

        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/asr/stream");
        processData.setApikey("test-key");
        WebSocketSession session = mock(WebSocketSession.class);
        RealTimeHandler handler = new RealTimeHandler("wss://upstream", new AsrProperty(), processData, null, adaptor,
                CredentialSession.NOOP, null);

        handler.handleTextMessage(session, new TextMessage("{\"header\":{\"name\":\"StartTranscription\"},"
                + "\"payload\":{\"format\":\"pcm\",\"sample_rate\":16000,"
                + "\"speech_context\":{\"type\":\"provider-defined\",\"items\":[]}}}"));

        verify(adaptor).startTranscription(anyString(), any(), any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void failedStartCanBeRetriedOnTheSameConnection() throws Exception {
        RealTimeAdaptor<AsrProperty> adaptor = mock(RealTimeAdaptor.class);
        Callbacks.WebSocketCallback callback = mock(Callbacks.WebSocketCallback.class);
        WebSocket upstream = mock(WebSocket.class);
        when(adaptor.createCallback(any(), any(), any(), anyString(), any(), any())).thenReturn(callback);
        when(adaptor.startTranscription(anyString(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("invalid request"))
                .thenReturn(upstream);

        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("/v1/audio/asr/stream");
        processData.setApikey("test-key");
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        RealTimeHandler handler = new RealTimeHandler("wss://upstream", new AsrProperty(), processData, null, adaptor,
                CredentialSession.NOOP, null);

        TextMessage start = new TextMessage("{\"header\":{\"name\":\"StartTranscription\"},"
                + "\"payload\":{\"format\":\"pcm\",\"sample_rate\":16000}}}");

        handler.handleTextMessage(session, start);
        handler.handleTextMessage(session, start);

        verify(adaptor, times(2)).startTranscription(anyString(), any(), any(), any());
        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, times(2)).sendMessage(messages.capture());
        assertTrue(messages.getAllValues().get(0).getPayload().contains("invalid request"));
        assertTrue(messages.getAllValues().get(1).getPayload().contains("TranscriptionStarted"));
        RealTimeMessage failedResponse = JacksonUtils.deserialize(messages.getAllValues().get(0).getPayload(), RealTimeMessage.class);
        assertTrue(failedResponse.getHeader().getTaskId() != null && !failedResponse.getHeader().getTaskId().isEmpty());
    }
}
