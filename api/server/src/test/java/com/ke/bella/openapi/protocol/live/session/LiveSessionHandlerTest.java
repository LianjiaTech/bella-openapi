package com.ke.bella.openapi.protocol.live.session;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.live.LiveSessionEventType;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import com.ke.bella.openapi.protocol.live.LiveSessionUsage;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.After;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LiveSessionHandlerTest {
    @After
    public void clearEndpointContext() {
        EndpointContext.clearAll();
    }

    @Test
    public void providerReadyActivatesAndIsForwardedOnce() throws Exception {
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), mock(EndpointLogger.class));
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setModel("gpt-live-1");
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "processData", processData);
        ReflectionTestUtils.setField(handler, "state", LiveSessionState.CONNECTING_PROVIDER);

        LiveSessionMessage started = LiveSessionMessage.event(LiveSessionEventType.SESSION_STARTED, "provider-session");
        handler.onProviderMessage(started);
        handler.onProviderMessage(LiveSessionMessage.event(LiveSessionEventType.SESSION_STARTED, "duplicate"));

        assertEquals(LiveSessionState.ACTIVE, ReflectionTestUtils.getField(handler, "state"));
        verify(client, times(1)).sendMessage(any());
    }

    @Test
    public void returnsFallbackUsageWhenProviderClosesWithoutFinalUsage() throws Exception {
        EndpointLogger endpointLogger = mock(EndpointLogger.class);
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), endpointLogger);
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId("request-1");
        processData.setBellaTraceId("trace-1");
        processData.setMetrics(new HashMap<>());
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "processData", processData);
        ReflectionTestUtils.setField(handler, "state", LiveSessionState.CLOSING);
        long startedAt = System.currentTimeMillis() - 43000L;
        ReflectionTestUtils.setField(handler, "startedAt", startedAt);
        ReflectionTestUtils.setField(handler, "clientCloseAt", startedAt + 12000L);

        handler.onProviderClosed(1000, "");

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(client, atLeastOnce()).sendMessage(messageCaptor.capture());
        LiveSessionMessage closed = JacksonUtils.deserialize(messageCaptor.getValue().getPayload(), LiveSessionMessage.class);
        assertEquals(LiveSessionEventType.SESSION_CLOSED, closed.eventType());
        assertEquals(Long.valueOf(12L), closed.getUsage().getSessionDurationSeconds());
        assertFalse(closed.getUsage().getFinalUsageConfirmed());
        assertEquals(closed.getUsage(), processData.getUsage());
        Map<?, ?> liveTrace = (Map<?, ?>) processData.getMetrics().get("live_trace");
        assertEquals("request-1", liveTrace.get("request_id"));
        assertEquals("trace-1", liveTrace.get("trace_id"));
        verify(endpointLogger).log(processData);
    }

    @Test
    public void returnsFallbackUsageWhenProviderReportsZeroDuration() throws Exception {
        EndpointLogger endpointLogger = mock(EndpointLogger.class);
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), endpointLogger);
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setMetrics(new HashMap<>());
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "processData", processData);
        ReflectionTestUtils.setField(handler, "state", LiveSessionState.CLOSING);
        long startedAt = System.currentTimeMillis() - 43000L;
        ReflectionTestUtils.setField(handler, "startedAt", startedAt);
        ReflectionTestUtils.setField(handler, "clientCloseAt", startedAt + 12000L);

        LiveSessionUsage usage = new LiveSessionUsage();
        usage.setSessionDurationSeconds(0L);
        usage.setFinalUsageConfirmed(true);
        LiveSessionMessage closed = LiveSessionMessage.event(LiveSessionEventType.SESSION_CLOSED, null);
        closed.setUsage(usage);

        handler.onProviderMessage(closed);

        LiveSessionUsage finalUsage = (LiveSessionUsage) processData.getUsage();
        assertEquals(Long.valueOf(12L), finalUsage.getSessionDurationSeconds());
        assertFalse(finalUsage.getFinalUsageConfirmed());
        verify(endpointLogger).log(processData);
    }

    @Test
    public void keepsClosingSessionAfterClientDisconnect() throws Exception {
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), mock(EndpointLogger.class));
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "state", LiveSessionState.CLOSING);

        ReflectionTestUtils.invokeMethod(handler, "disconnectClient", CloseStatus.NORMAL);
        handler.afterConnectionClosed(client, CloseStatus.NORMAL);

        assertEquals(LiveSessionState.CLOSING, ReflectionTestUtils.getField(handler, "state"));
        assertFalse(((java.util.concurrent.atomic.AtomicBoolean) ReflectionTestUtils.getField(handler, "closed")).get());
        verify(client, times(1)).close(CloseStatus.NORMAL);
    }

    @Test
    public void forwardsProviderOutputWhileClosing() throws Exception {
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), mock(EndpointLogger.class));
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setMetrics(new HashMap<>());
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "processData", processData);
        ReflectionTestUtils.setField(handler, "state", LiveSessionState.CLOSING);

        LiveSessionMessage audio = LiveSessionMessage.event(LiveSessionEventType.OUTPUT_AUDIO_DELTA, null);
        audio.setAudio("AQID");
        handler.onProviderMessage(audio);

        verify(client, times(1)).sendMessage(any());
    }

    @Test
    public void treatsProviderFailureDuringCloseAsGracefulClose() throws Exception {
        EndpointLogger endpointLogger = mock(EndpointLogger.class);
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), endpointLogger);
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setMetrics(new HashMap<>());
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "processData", processData);
        ReflectionTestUtils.setField(handler, "state", LiveSessionState.CLOSING);

        handler.onProviderFailure(new java.io.EOFException("upstream closed"));

        ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(client, times(1)).sendMessage(messageCaptor.capture());
        LiveSessionMessage closed = JacksonUtils.deserialize(messageCaptor.getValue().getPayload(), LiveSessionMessage.class);
        assertEquals(LiveSessionEventType.SESSION_CLOSED, closed.eventType());
        assertFalse(closed.getUsage().getFinalUsageConfirmed());
        assertEquals("provider_closed_during_close", processData.getMetrics().get("live_close_reason"));
        verify(client).close(CloseStatus.NORMAL);
        verify(endpointLogger).log(processData);
    }

    @Test
    public void forwardsProviderConfirmedUsageWithoutFallbackDuplicate() throws Exception {
        EndpointLogger endpointLogger = mock(EndpointLogger.class);
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), endpointLogger);
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setMetrics(new HashMap<>());
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "processData", processData);
        ReflectionTestUtils.setField(handler, "state", LiveSessionState.CLOSING);
        ReflectionTestUtils.setField(handler, "startedAt", System.currentTimeMillis() - 43000L);
        LiveSessionUsage usage = new LiveSessionUsage();
        usage.setSessionDurationSeconds(2L);
        usage.setFinalUsageConfirmed(true);
        LiveSessionMessage closed = LiveSessionMessage.event(LiveSessionEventType.SESSION_CLOSED, null);
        closed.setUsage(usage);

        handler.onProviderMessage(closed);

        verify(client, times(1)).sendMessage(any());
        LiveSessionUsage finalUsage = (LiveSessionUsage) processData.getUsage();
        assertEquals(Long.valueOf(2L), finalUsage.getSessionDurationSeconds());
        assertTrue(finalUsage.getFinalUsageConfirmed());
        verify(endpointLogger).log(processData);
    }

    @Test
    public void pongRefreshesClientLiveness() {
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), mock(EndpointLogger.class));
        WebSocketSession client = mock(WebSocketSession.class);
        ReflectionTestUtils.setField(handler, "client", client);
        AtomicLong lastPongNanos = (AtomicLong) ReflectionTestUtils.getField(handler, "lastClientPongNanos");
        lastPongNanos.set(System.nanoTime() - TimeUnit.SECONDS.toNanos(31));

        handler.handlePongMessage(client, new PongMessage(ByteBuffer.allocate(0)));

        assertTrue(lastPongNanos.get() > System.nanoTime() - TimeUnit.SECONDS.toNanos(1));
    }

    @Test
    public void heartbeatTimeoutClosesClientSession() throws Exception {
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), mock(EndpointLogger.class));
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        ReflectionTestUtils.setField(handler, "client", client);
        AtomicLong lastPongNanos = (AtomicLong) ReflectionTestUtils.getField(handler, "lastClientPongNanos");
        lastPongNanos.set(System.nanoTime() - TimeUnit.SECONDS.toNanos(31));

        ReflectionTestUtils.invokeMethod(handler, "checkClientHeartbeat");

        assertEquals(LiveSessionState.CLOSED, ReflectionTestUtils.getField(handler, "state"));
        verify(client).close(CloseStatus.SESSION_NOT_RELIABLE);
    }

    @Test
    public void businessIdleTimeoutClosesActiveSession() throws Exception {
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), mock(EndpointLogger.class));
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setMetrics(new HashMap<>());
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "processData", processData);
        ReflectionTestUtils.setField(handler, "state", LiveSessionState.ACTIVE);
        AtomicLong lastBusinessActivityNanos = (AtomicLong) ReflectionTestUtils.getField(handler, "lastBusinessActivityNanos");
        lastBusinessActivityNanos.set(System.nanoTime() - TimeUnit.MINUTES.toNanos(6));

        ReflectionTestUtils.invokeMethod(handler, "checkBusinessIdleTimeout");

        assertEquals(LiveSessionState.CLOSED, ReflectionTestUtils.getField(handler, "state"));
        verify(client).close(CloseStatus.SESSION_NOT_RELIABLE);
        assertEquals("idle_timeout", processData.getMetrics().get("live_close_reason"));
    }

    @Test
    public void clearsEndpointContextAfterClientMessage() throws Exception {
        LiveSessionHandler handler = new LiveSessionHandler(new EndpointProcessData(), mock(ApikeyInfo.class), mock(ChannelRouter.class),
                mock(AdaptorManager.class), mock(LiveSessionAdmissionService.class), mock(EndpointLogger.class));
        WebSocketSession client = mock(WebSocketSession.class);
        when(client.isOpen()).thenReturn(true);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setMetrics(new HashMap<>());
        ReflectionTestUtils.setField(handler, "client", client);
        ReflectionTestUtils.setField(handler, "processData", processData);

        handler.handleTextMessage(client, new TextMessage("{}"));

        assertNotSame(processData, EndpointContext.getProcessData());
    }
}
