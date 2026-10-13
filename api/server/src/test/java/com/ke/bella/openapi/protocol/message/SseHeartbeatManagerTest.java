package com.ke.bella.openapi.protocol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.log.EndpointLogger;

public class SseHeartbeatManagerTest {
    private static final long HEARTBEAT_INTERVAL_MILLIS = 100;

    private ScheduledExecutorService scheduler;
    private AtomicLong currentTimeMillis;
    private Runnable heartbeatScanner;
    private SseHeartbeatManager heartbeatManager;
    private List<StreamMessageResponse> events;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
        currentTimeMillis = new AtomicLong();
        events = new ArrayList<>();
        when(scheduler.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    heartbeatScanner = invocation.getArgument(0);
                    return scheduledFuture;
                });
        heartbeatManager = new SseHeartbeatManager(scheduler, currentTimeMillis::get, 10);
    }

    @Test
    public void multipleConnectionsShareSingleScanner() {
        heartbeatManager.register(mock(SseEmitter.class), HEARTBEAT_INTERVAL_MILLIS, () -> { });
        heartbeatManager.register(mock(SseEmitter.class), HEARTBEAT_INTERVAL_MILLIS, () -> { });

        verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(10L), eq(10L), eq(TimeUnit.MILLISECONDS));
        assertEquals(2, heartbeatManager.connectionCount());
    }

    @Test
    public void sendsPingAfterIdleInterval() {
        heartbeatManager.register(mock(SseEmitter.class), HEARTBEAT_INTERVAL_MILLIS,
                () -> events.add(StreamMessageResponse.ping()));

        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS);
        heartbeatScanner.run();

        assertEquals(1, events.size());
        assertEquals("ping", events.get(0).getType());
    }

    @Test
    public void doesNotSendPingBeforeConfiguredIdleInterval() {
        heartbeatManager.register(mock(SseEmitter.class), HEARTBEAT_INTERVAL_MILLIS,
                () -> events.add(StreamMessageResponse.ping()));

        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS - 1);
        heartbeatScanner.run();

        assertTrue(events.isEmpty());
    }

    @Test
    public void customIntervalControlsHeartbeatTriggerTime() {
        long customIntervalMillis = 300;
        heartbeatManager.register(mock(SseEmitter.class), customIntervalMillis,
                () -> events.add(StreamMessageResponse.ping()));

        currentTimeMillis.set(customIntervalMillis - 1);
        heartbeatScanner.run();
        assertTrue(events.isEmpty());

        currentTimeMillis.set(customIntervalMillis);
        heartbeatScanner.run();
        assertEquals(1, events.size());
        assertEquals("ping", events.get(0).getType());
    }

    @Test
    public void sentPingStartsNextIdleInterval() {
        heartbeatManager.register(mock(SseEmitter.class), HEARTBEAT_INTERVAL_MILLIS,
                () -> events.add(StreamMessageResponse.ping()));

        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS);
        heartbeatScanner.run();
        assertEquals(1, events.size());

        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS * 2 - 1);
        heartbeatScanner.run();
        assertEquals(1, events.size());

        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS * 2);
        heartbeatScanner.run();
        assertEquals(2, events.size());
    }

    @Test
    public void activityResetsIdleInterval() {
        SseHeartbeatManager.Registration registration = heartbeatManager.register(
                mock(SseEmitter.class), HEARTBEAT_INTERVAL_MILLIS,
                () -> events.add(StreamMessageResponse.ping()));

        currentTimeMillis.set(60);
        registration.recordActivity();
        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS);
        heartbeatScanner.run();
        assertTrue(events.isEmpty());

        currentTimeMillis.set(160);
        heartbeatScanner.run();
        assertEquals(1, events.size());
    }

    @Test
    public void unregisterStopsHeartbeatAndRemovesConnection() {
        SseHeartbeatManager.Registration registration = heartbeatManager.register(
                mock(SseEmitter.class), HEARTBEAT_INTERVAL_MILLIS,
                () -> events.add(StreamMessageResponse.ping()));

        registration.unregister();
        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS);
        heartbeatScanner.run();

        assertTrue(events.isEmpty());
        assertEquals(0, heartbeatManager.connectionCount());
    }

    @Test
    public void emitterCompletionUnregistersConnection() {
        SseEmitter sse = mock(SseEmitter.class);
        List<Runnable> completionCallbacks = new ArrayList<>();
        doAnswer(invocation -> {
            completionCallbacks.add(invocation.getArgument(0));
            return null;
        }).when(sse).onCompletion(any(Runnable.class));

        StreamMessagesCallback callback = new StreamMessagesCallback(sse, new EndpointProcessData(), null, null, null,
                heartbeatManager, true, HEARTBEAT_INTERVAL_MILLIS);
        callback.onOpen();
        assertEquals(1, heartbeatManager.connectionCount());

        completionCallbacks.get(0).run();

        assertEquals(0, heartbeatManager.connectionCount());
    }

    @Test
    public void emitterCompletionBeforeOpenPreventsLateRegistration() {
        SseEmitter sse = mock(SseEmitter.class);
        List<Runnable> completionCallbacks = new ArrayList<>();
        doAnswer(invocation -> {
            completionCallbacks.add(invocation.getArgument(0));
            return null;
        }).when(sse).onCompletion(any(Runnable.class));

        StreamMessagesCallback callback = new StreamMessagesCallback(sse, new EndpointProcessData(), null, null, null,
                heartbeatManager, true, HEARTBEAT_INTERVAL_MILLIS);
        completionCallbacks.get(0).run();
        callback.onOpen();

        assertEquals(0, heartbeatManager.connectionCount());
    }

    @Test
    public void disabledHeartbeatDoesNotRegisterOrSendAfterInterval() throws Exception {
        SseEmitter sse = mock(SseEmitter.class);
        StreamMessagesCallback callback = new StreamMessagesCallback(sse, new EndpointProcessData(),
                null, null, null, heartbeatManager, false, HEARTBEAT_INTERVAL_MILLIS);
        callback.onOpen();

        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS * 2);
        heartbeatScanner.run();

        assertEquals(0, heartbeatManager.connectionCount());
        verify(sse, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    public void repeatedOpenRegistersConnectionOnlyOnce() {
        StreamMessagesCallback callback = new StreamMessagesCallback(mock(SseEmitter.class),
                new EndpointProcessData(), null, mock(EndpointLogger.class), null,
                heartbeatManager, true, HEARTBEAT_INTERVAL_MILLIS);

        try {
            callback.onOpen();
            callback.onOpen();

            assertEquals(1, heartbeatManager.connectionCount());
        } finally {
            callback.finish();
        }
    }

    @Test
    public void runtimeSendFailureUnregistersConnection() throws Exception {
        SseEmitter sse = mock(SseEmitter.class);
        doThrow(new IllegalStateException("ResponseBodyEmitter has already completed"))
                .when(sse).send(any(SseEmitter.SseEventBuilder.class));
        StreamMessagesCallback callback = new StreamMessagesCallback(sse, new EndpointProcessData(),
                null, mock(EndpointLogger.class), null, heartbeatManager, true, HEARTBEAT_INTERVAL_MILLIS);
        callback.onOpen();

        try {
            currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS);
            heartbeatScanner.run();

            assertEquals(0, heartbeatManager.connectionCount());
        } finally {
            callback.finish();
        }
    }

    @Test
    public void runtimeHeartbeatSenderFailureUnregistersConnection() {
        heartbeatManager.register(mock(SseEmitter.class), HEARTBEAT_INTERVAL_MILLIS,
                () -> { throw new IllegalStateException("heartbeat sender failed"); });

        currentTimeMillis.set(HEARTBEAT_INTERVAL_MILLIS);
        heartbeatScanner.run();

        assertEquals(0, heartbeatManager.connectionCount());
    }

    @Test
    public void completionPropertyProvidesChannelHeartbeatConfiguration() {
        CompletionProperty property = new CompletionProperty();
        assertFalse(property.isMessageSseHeartbeatEnabled());
        assertEquals(15, property.getMessageSseHeartbeatIntervalSeconds());

        property.setMessageSseHeartbeatEnabled(true);
        property.setMessageSseHeartbeatIntervalSeconds(30);

        assertTrue(property.isMessageSseHeartbeatEnabled());
        assertEquals(30, property.getMessageSseHeartbeatIntervalSeconds());
    }

    @Test
    public void streamCallbackAppliesConfiguredHeartbeatInterval() {
        CompletionProperty property = new CompletionProperty();
        property.setMessageSseHeartbeatEnabled(true);
        property.setMessageSseHeartbeatIntervalSeconds(30);

        StreamMessagesCallback callback = new StreamMessagesCallback(mock(SseEmitter.class),
                new EndpointProcessData(), null, null, null, property);

        assertEquals(true, ReflectionTestUtils.getField(callback, "heartbeatEnabled"));
        assertEquals(TimeUnit.SECONDS.toMillis(30), ReflectionTestUtils.getField(callback, "heartbeatIntervalMillis"));
    }
}
