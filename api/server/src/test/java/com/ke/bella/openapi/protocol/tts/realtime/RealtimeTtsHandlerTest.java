package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.Callbacks.WebSocketCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RealtimeTtsHandlerTest {
    @Test
    public void startSpeechRequiresPayloadModel() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        ApikeyInfo apikey = mock(ApikeyInfo.class);
        RealtimeTtsHandler handler = new RealtimeTtsHandler(new EndpointProcessData(), apikey, null, null, logger);
        WebSocketSession clientSession = mock(WebSocketSession.class);
        when(clientSession.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(clientSession);

        RealtimeTtsMessage start = RealtimeTtsMessage.builder()
                .header(RealtimeTtsHeader.builder().name(RealtimeTtsEventType.START_SPEECH.getValue()).taskId("task-a").build())
                .payload(RealtimeTtsPayload.builder().build())
                .build();

        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(start)));

        ArgumentCaptor<WebSocketMessage> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(clientSession).sendMessage(captor.capture());
        RealtimeTtsMessage response = JacksonUtils.deserialize(((TextMessage) captor.getValue()).getPayload(), RealtimeTtsMessage.class);
        assertEquals(RealtimeTtsEventType.SPEECH_FAILED.getValue(), response.name());
        assertEquals(RealtimeTtsMessage.STATUS_CLIENT_ERROR, response.getHeader().getStatus().intValue());
        assertEquals("missing_model", response.getPayload().getError().getCode());
    }

    @Test
    public void clientAudioSendFailureFinalizesAndClosesUpstream() {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsHandler handler = new RealtimeTtsHandler(new EndpointProcessData(), null, null, null, logger);
        WebSocketSession clientSession = mock(WebSocketSession.class);
        when(clientSession.isOpen()).thenReturn(false);
        handler.afterConnectionEstablished(clientSession);

        WebSocket upstream = mock(WebSocket.class);
        RealtimeTtsAdaptor adaptor = mock(RealtimeTtsAdaptor.class);
        RealtimeTtsTaskContext taskContext = taskContext(upstream);
        ReflectionTestUtils.setField(handler, "activeTask", taskContext);
        RealtimeTtsUpstreamConnection connection = upstreamConnection(adaptor, upstream);
        connection.register(taskContext);
        connection.setActiveProviderSessionId(taskContext.getProviderSessionId());
        ReflectionTestUtils.setField(handler, "upstreamConnection", connection);

        handler.onAudio(taskContext, new byte[] { 1, 2, 3, 4 }, 10L);

        assertTrue(taskContext.getFinalized().get());
        assertEquals(RealtimeTtsTaskContext.State.COMPLETED, taskContext.getState());
        assertNull(ReflectionTestUtils.getField(handler, "activeTask"));
        assertNull(ReflectionTestUtils.getField(handler, "upstreamConnection"));
        verify(adaptor).closeConnection(upstream);
        verify(logger).log(any(EndpointProcessData.class));
    }

    @Test
    public void startSpeechRaceClosesUpstreamWhenTaskFinalizedBeforeWebSocketAssigned() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsAdmissionService admissionService = mock(RealtimeTtsAdmissionService.class);
        AdaptorManager adaptorManager = mock(AdaptorManager.class);
        ApikeyInfo apikey = mock(ApikeyInfo.class);
        RaceAdaptor adaptor = new RaceAdaptor();
        RealtimeTtsHandler handler = new RealtimeTtsHandler(new EndpointProcessData(), apikey,
                adaptorManager, admissionService, logger);
        WebSocketSession clientSession = mock(WebSocketSession.class);
        when(clientSession.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(clientSession);

        ChannelDB channel = new ChannelDB();
        channel.setChannelInfo("{}");
        when(admissionService.admit(eq(RealtimeTtsConstants.ENDPOINT), eq("model-a"), any(ApikeyInfo.class), eq(false)))
                .thenAnswer(invocation -> {
                    EndpointProcessData processData = EndpointContext.getProcessData();
                    processData.setProtocol("RaceAdaptor");
                    processData.setForwardUrl("ws://provider");
                    processData.setChannelCode("ch-race");
                    return channel;
                });
        when(adaptorManager.getProtocolAdaptor(eq(RealtimeTtsConstants.ENDPOINT), eq("RaceAdaptor"), eq(RealtimeTtsAdaptor.class)))
                .thenReturn(adaptor);

        RealtimeTtsMessage start = RealtimeTtsMessage.builder()
                .header(RealtimeTtsHeader.builder().name(RealtimeTtsEventType.START_SPEECH.getValue()).taskId("task-a").build())
                .payload(RealtimeTtsPayload.builder().model("model-a").build())
                .build();

        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(start)));

        assertEquals(1, adaptor.closeCount);
        assertNull(ReflectionTestUtils.getField(handler, "activeTask"));
        assertNull(ReflectionTestUtils.getField(handler, "upstreamConnection"));
        verify(logger).log(any(EndpointProcessData.class));
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void sequentialTasksUseStartSpeechPayloadModelIndependently() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsAdmissionService admissionService = mock(RealtimeTtsAdmissionService.class);
        AdaptorManager adaptorManager = mock(AdaptorManager.class);
        ApikeyInfo apikey = mock(ApikeyInfo.class);
        RealtimeTtsAdaptor adaptor = mock(RealtimeTtsAdaptor.class);
        WebSocket upstreamA = mock(WebSocket.class);
        WebSocket upstreamB = mock(WebSocket.class);
        RealtimeTtsHandler handler = new RealtimeTtsHandler(new EndpointProcessData(), apikey,
                adaptorManager, admissionService, logger);
        WebSocketSession clientSession = mock(WebSocketSession.class);
        when(clientSession.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(clientSession);

        ChannelDB channel = new ChannelDB();
        channel.setChannelInfo("{}");
        when(admissionService.admit(eq(RealtimeTtsConstants.ENDPOINT), any(String.class), any(ApikeyInfo.class), eq(false)))
                .thenAnswer(invocation -> {
                    String model = invocation.getArgument(1);
                    EndpointProcessData processData = EndpointContext.getProcessData();
                    processData.setProtocol("MockAdaptor");
                    processData.setForwardUrl("ws://provider/" + model);
                    processData.setChannelCode("ch-" + model);
                    processData.setPriceInfo("{\"input\":1}");
                    return channel;
                });
        when(adaptorManager.getProtocolAdaptor(eq(RealtimeTtsConstants.ENDPOINT), eq("MockAdaptor"), eq(RealtimeTtsAdaptor.class)))
                .thenReturn(adaptor);
        when(adaptor.getPropertyClass()).thenReturn(RealtimeTtsProperty.class);
        when(adaptor.capability(any(RealtimeTtsMessage.class))).thenReturn(new RealtimeTtsCapability());
        when(adaptor.createCallback(any(RealtimeTtsTaskListener.class), any(RealtimeTtsTaskContext.class), any(RealtimeTtsProperty.class)))
                .thenReturn(mock(WebSocketCallback.class));
        when(adaptor.startConnection(any(String.class), any(RealtimeTtsProperty.class), any(RealtimeTtsMessage.class), any(WebSocketCallback.class)))
                .thenReturn(upstreamA, upstreamB);

        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-a", "model-a"))));
        RealtimeTtsTaskContext taskA = (RealtimeTtsTaskContext) ReflectionTestUtils.getField(handler, "activeTask");
        assertEquals("model-a", taskA.getProcessData().getModel());
        assertEquals("model-a", taskA.getFrozenPayload().getModel());
        handler.onCompleted(taskA, usage(3));

        assertNull(ReflectionTestUtils.getField(handler, "activeTask"));

        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-b", "model-b"))));
        RealtimeTtsTaskContext taskB = (RealtimeTtsTaskContext) ReflectionTestUtils.getField(handler, "activeTask");
        assertEquals("model-b", taskB.getProcessData().getModel());
        assertEquals("model-b", taskB.getFrozenPayload().getModel());
        handler.onCompleted(taskB, usage(5));

        verify(admissionService).admit(eq(RealtimeTtsConstants.ENDPOINT), eq("model-a"), any(ApikeyInfo.class), eq(false));
        verify(admissionService).admit(eq(RealtimeTtsConstants.ENDPOINT), eq("model-b"), any(ApikeyInfo.class), eq(false));
        ArgumentCaptor<EndpointProcessData> logCaptor = ArgumentCaptor.forClass(EndpointProcessData.class);
        verify(logger, times(2)).log(logCaptor.capture());
        assertEquals("model-a", logCaptor.getAllValues().get(0).getModel());
        assertEquals(3, ((Map<?, ?>) logCaptor.getAllValues().get(0).getUsage()).get("input_characters"));
        assertEquals("model-b", logCaptor.getAllValues().get(1).getModel());
        assertEquals(5, ((Map<?, ?>) logCaptor.getAllValues().get(1).getUsage()).get("input_characters"));
    }

    @Test
    public void sequentialReusableTasksKeepSameUpstreamAndStartNewSession() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsAdmissionService admissionService = mock(RealtimeTtsAdmissionService.class);
        AdaptorManager adaptorManager = mock(AdaptorManager.class);
        ApikeyInfo apikey = mock(ApikeyInfo.class);
        TestAdaptor adaptor = new TestAdaptor();
        RealtimeTtsHandler handler = handler(adaptor, admissionService, adaptorManager, apikey, logger);

        ChannelDB channel = new ChannelDB();
        channel.setChannelInfo("{}");
        when(admissionService.admit(eq(RealtimeTtsConstants.ENDPOINT), any(String.class), any(ApikeyInfo.class), eq(false)))
                .thenAnswer(invocation -> {
                    EndpointProcessData processData = EndpointContext.getProcessData();
                    processData.setProtocol("TestAdaptor");
                    processData.setForwardUrl("ws://provider/shared");
                    processData.setChannelCode("ch-shared");
                    return channel;
                });

        WebSocketSession clientSession = clientSession(handler);
        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-a", "model-a"))));
        RealtimeTtsTaskContext taskA = (RealtimeTtsTaskContext) ReflectionTestUtils.getField(handler, "activeTask");
        handler.onCompleted(taskA, usage(3));

        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-b", "model-a"))));
        RealtimeTtsTaskContext taskB = (RealtimeTtsTaskContext) ReflectionTestUtils.getField(handler, "activeTask");

        assertEquals(1, adaptor.startConnectionCount);
        assertEquals(1, adaptor.startSessionCount);
        assertTrue(Boolean.TRUE.equals(taskB.getMetrics().get("upstream_reused")));
        assertEquals(taskA.getUpstream(), taskB.getUpstream());
        assertTrue(!taskA.getProviderSessionId().equals(taskB.getProviderSessionId()));
    }

    @Test
    public void connectionReuseFalseClosesCompletedTaskUpstream() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsAdmissionService admissionService = mock(RealtimeTtsAdmissionService.class);
        AdaptorManager adaptorManager = mock(AdaptorManager.class);
        TestAdaptor adaptor = new TestAdaptor();
        RealtimeTtsHandler handler = handler(adaptor, admissionService, adaptorManager, mock(ApikeyInfo.class), logger);

        ChannelDB channel = new ChannelDB();
        channel.setChannelInfo("{\"connectionReuse\":false}");
        when(admissionService.admit(eq(RealtimeTtsConstants.ENDPOINT), any(String.class), any(ApikeyInfo.class), eq(false)))
                .thenAnswer(invocation -> {
                    EndpointProcessData processData = EndpointContext.getProcessData();
                    processData.setProtocol("TestAdaptor");
                    processData.setForwardUrl("ws://provider/shared");
                    processData.setChannelCode("ch-shared");
                    return channel;
                });

        WebSocketSession clientSession = clientSession(handler);
        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-a", "model-a"))));
        handler.onCompleted((RealtimeTtsTaskContext) ReflectionTestUtils.getField(handler, "activeTask"), usage(3));
        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-b", "model-a"))));

        assertEquals(2, adaptor.startConnectionCount);
        assertEquals(1, adaptor.closeCount);
    }

    @Test
    public void connectionKeyChangeClosesPreviousReusableUpstream() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsAdmissionService admissionService = mock(RealtimeTtsAdmissionService.class);
        AdaptorManager adaptorManager = mock(AdaptorManager.class);
        TestAdaptor adaptor = new TestAdaptor();
        RealtimeTtsHandler handler = handler(adaptor, admissionService, adaptorManager, mock(ApikeyInfo.class), logger);

        ChannelDB channel = new ChannelDB();
        channel.setChannelInfo("{}");
        when(admissionService.admit(eq(RealtimeTtsConstants.ENDPOINT), any(String.class), any(ApikeyInfo.class), eq(false)))
                .thenAnswer(invocation -> {
                    String model = invocation.getArgument(1);
                    EndpointProcessData processData = EndpointContext.getProcessData();
                    processData.setProtocol("TestAdaptor");
                    processData.setForwardUrl("ws://provider/" + model);
                    processData.setChannelCode("ch-shared");
                    return channel;
                });

        WebSocketSession clientSession = clientSession(handler);
        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-a", "model-a"))));
        handler.onCompleted((RealtimeTtsTaskContext) ReflectionTestUtils.getField(handler, "activeTask"), usage(3));
        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-b", "model-b"))));

        assertEquals(2, adaptor.startConnectionCount);
        assertEquals(1, adaptor.closeCount);
    }

    @Test
    public void lateCompletedTaskDoesNotClearNewActiveTaskOnReusedConnection() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsAdmissionService admissionService = mock(RealtimeTtsAdmissionService.class);
        AdaptorManager adaptorManager = mock(AdaptorManager.class);
        TestAdaptor adaptor = new TestAdaptor();
        RealtimeTtsHandler handler = handler(adaptor, admissionService, adaptorManager, mock(ApikeyInfo.class), logger);

        ChannelDB channel = new ChannelDB();
        channel.setChannelInfo("{}");
        when(admissionService.admit(eq(RealtimeTtsConstants.ENDPOINT), any(String.class), any(ApikeyInfo.class), eq(false)))
                .thenAnswer(invocation -> {
                    EndpointProcessData processData = EndpointContext.getProcessData();
                    processData.setProtocol("TestAdaptor");
                    processData.setForwardUrl("ws://provider/shared");
                    processData.setChannelCode("ch-shared");
                    return channel;
                });

        WebSocketSession clientSession = clientSession(handler);
        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-a", "model-a"))));
        RealtimeTtsTaskContext taskA = (RealtimeTtsTaskContext) ReflectionTestUtils.getField(handler, "activeTask");
        handler.onCompleted(taskA, usage(3));
        handler.handleTextMessage(clientSession, new TextMessage(JacksonUtils.serialize(startSpeech("task-b", "model-a"))));
        RealtimeTtsTaskContext taskB = (RealtimeTtsTaskContext) ReflectionTestUtils.getField(handler, "activeTask");

        handler.onCompleted(taskA, usage(9));

        assertEquals(taskB, ReflectionTestUtils.getField(handler, "activeTask"));
        RealtimeTtsUpstreamConnection connection = (RealtimeTtsUpstreamConnection) ReflectionTestUtils.getField(handler, "upstreamConnection");
        assertEquals(taskB.getProviderSessionId(), connection.getActiveProviderSessionId());
    }

    @Test
    public void idleReusableConnectionIsClosedWhenTimeoutExpires() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsHandler handler = new RealtimeTtsHandler(new EndpointProcessData(), null, null, null, logger);
        WebSocketSession clientSession = clientSession(handler);
        WebSocket upstream = mock(WebSocket.class);
        RealtimeTtsAdaptor adaptor = mock(RealtimeTtsAdaptor.class);
        RealtimeTtsTaskContext taskContext = taskContext(upstream);
        RealtimeTtsUpstreamConnection connection = upstreamConnection(adaptor, upstream);
        taskContext.setUpstreamConnection(connection);
        taskContext.getMetrics().put("connection_reuse_enabled", true);
        connection.register(taskContext);
        connection.setActiveProviderSessionId(taskContext.getProviderSessionId());
        ReflectionTestUtils.setField(handler, "activeTask", taskContext);
        ReflectionTestUtils.setField(handler, "upstreamConnection", connection);

        handler.onCompleted(taskContext, usage(3));

        assertEquals(connection, ReflectionTestUtils.getField(handler, "upstreamConnection"));
        assertTrue(ReflectionTestUtils.getField(handler, "upstreamIdleCloseFuture") != null);
        long expiredAt = System.currentTimeMillis() - RealtimeTtsUpstreamConnection.DEFAULT_IDLE_TIMEOUT_MS - 1;
        connection.markUsed(expiredAt);
        ReflectionTestUtils.invokeMethod(handler, "closeIdleUpstream", connection, expiredAt);

        assertNull(ReflectionTestUtils.getField(handler, "upstreamConnection"));
        verify(adaptor).closeConnection(upstream);
    }

    @Test
    public void lateCloseForOldConnectionDoesNotCloseReplacementConnection() {
        RealtimeTtsHandler handler = new RealtimeTtsHandler(new EndpointProcessData(), null, null, null, mock(EndpointLogger.class));
        RealtimeTtsAdaptor oldAdaptor = mock(RealtimeTtsAdaptor.class);
        RealtimeTtsAdaptor newAdaptor = mock(RealtimeTtsAdaptor.class);
        WebSocket oldWebSocket = mock(WebSocket.class);
        WebSocket newWebSocket = mock(WebSocket.class);
        RealtimeTtsUpstreamConnection oldConnection = upstreamConnection(oldAdaptor, oldWebSocket);
        RealtimeTtsUpstreamConnection newConnection = upstreamConnection(newAdaptor, newWebSocket);
        ReflectionTestUtils.setField(handler, "upstreamConnection", newConnection);

        ReflectionTestUtils.invokeMethod(handler, "closeUpstream", oldConnection, "late_task_cleanup");

        assertEquals(newConnection, ReflectionTestUtils.getField(handler, "upstreamConnection"));
        verify(newAdaptor, never()).closeConnection(newWebSocket);
    }

    @Test
    public void providerSessionFailureKeepsHealthyReusableConnection() throws Exception {
        EndpointLogger logger = mock(EndpointLogger.class);
        RealtimeTtsHandler handler = new RealtimeTtsHandler(new EndpointProcessData(), null, null, null, logger);
        WebSocketSession clientSession = clientSession(handler);
        WebSocket upstream = mock(WebSocket.class);
        RealtimeTtsAdaptor adaptor = mock(RealtimeTtsAdaptor.class);
        RealtimeTtsTaskContext taskContext = taskContext(upstream);
        RealtimeTtsUpstreamConnection connection = upstreamConnection(adaptor, upstream);
        taskContext.setUpstreamConnection(connection);
        taskContext.setProviderSessionFailure(true);
        taskContext.getMetrics().put("connection_reuse_enabled", true);
        connection.register(taskContext);
        connection.setActiveProviderSessionId(taskContext.getProviderSessionId());
        ReflectionTestUtils.setField(handler, "activeTask", taskContext);
        ReflectionTestUtils.setField(handler, "upstreamConnection", connection);

        handler.onFailed(taskContext, "provider_error", "invalid session parameter", "BadRequest", "invalid session parameter");

        assertEquals(connection, ReflectionTestUtils.getField(handler, "upstreamConnection"));
        assertEquals("kept_after_session_failure", taskContext.getMetrics().get("upstream_close_reason"));
        verify(adaptor, never()).closeConnection(upstream);

        handler.afterConnectionClosed(clientSession, CloseStatus.NORMAL);
        verify(adaptor).closeConnection(upstream);
    }

    @Test
    public void freezePayloadPreservesStartSpeechExtraBody() {
        RealtimeTtsHandler handler = new RealtimeTtsHandler(new EndpointProcessData(), null, null, null, mock(EndpointLogger.class));
        Map<String, Object> extraBody = new LinkedHashMap<>();
        extraBody.put("additions", usage(7));
        RealtimeTtsProperty property = new RealtimeTtsProperty();
        property.setDefaultVoice("default-voice");
        property.setDefaultContentType("pcm");
        property.setDefaultSampleRate(24000);
        RealtimeTtsMessage start = RealtimeTtsMessage.builder()
                .payload(RealtimeTtsPayload.builder()
                        .model("model-a")
                        .extraBody(extraBody)
                        .build())
                .build();

        RealtimeTtsPayload frozen = ReflectionTestUtils.invokeMethod(handler, "freezePayload", start, property, "model-a");

        assertEquals(extraBody, frozen.getExtraBody());
    }

    private RealtimeTtsTaskContext taskContext(WebSocket upstream) {
        RealtimeTtsTaskContext taskContext = new RealtimeTtsTaskContext();
        taskContext.setSessionId("sess-a");
        taskContext.setTaskId("task-a");
        taskContext.setProviderSessionId("provider-session-a");
        taskContext.setStartMillis(1L);
        taskContext.setProcessData(new EndpointProcessData());
        taskContext.setFrozenPayload(RealtimeTtsPayload.builder()
                .format("pcm")
                .sampleRate(24000)
                .channels(1)
                .encoding("s16le")
                .build());
        taskContext.setUpstream(upstream);
        taskContext.getMetrics().put("input_characters", 0);
        return taskContext;
    }

    private RealtimeTtsUpstreamConnection upstreamConnection(RealtimeTtsAdaptor adaptor, WebSocket upstream) {
        RealtimeTtsUpstreamConnection connection = new RealtimeTtsUpstreamConnection(adaptor, mock(WebSocketCallback.class),
                RealtimeTtsUpstreamKey.of("MockAdaptor", "ch-a", "ws://provider", new RealtimeTtsProperty()), 1L);
        connection.setWebSocket(upstream);
        connection.setConnectionStarted(true);
        return connection;
    }

    private RealtimeTtsHandler handler(RealtimeTtsAdaptor adaptor, RealtimeTtsAdmissionService admissionService,
            AdaptorManager adaptorManager, ApikeyInfo apikey, EndpointLogger logger) {
        when(adaptorManager.getProtocolAdaptor(eq(RealtimeTtsConstants.ENDPOINT), eq("TestAdaptor"), eq(RealtimeTtsAdaptor.class)))
                .thenReturn(adaptor);
        return new RealtimeTtsHandler(new EndpointProcessData(), apikey, adaptorManager, admissionService, logger);
    }

    private WebSocketSession clientSession(RealtimeTtsHandler handler) {
        WebSocketSession clientSession = mock(WebSocketSession.class);
        when(clientSession.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(clientSession);
        return clientSession;
    }

    private RealtimeTtsMessage startSpeech(String taskId, String model) {
        return RealtimeTtsMessage.builder()
                .header(RealtimeTtsHeader.builder().name(RealtimeTtsEventType.START_SPEECH.getValue()).taskId(taskId).build())
                .payload(RealtimeTtsPayload.builder().model(model).build())
                .build();
    }

    private Map<String, Object> usage(int inputCharacters) {
        Map<String, Object> usage = new HashMap<>();
        usage.put("input_characters", inputCharacters);
        return usage;
    }

    private static class RaceAdaptor implements RealtimeTtsAdaptor<RealtimeTtsProperty> {
        private final WebSocket webSocket = mock(WebSocket.class);
        private int closeCount;

        @Override
        public WebSocket startSpeech(String url, RealtimeTtsProperty property, RealtimeTtsMessage request, WebSocketCallback callback) {
            RaceCallback raceCallback = (RaceCallback) callback;
            raceCallback.listener.onFailed(raceCallback.taskContext, "provider_error", "failed before assignment", "500", "failed");
            return webSocket;
        }

        @Override
        public boolean inputText(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
            return true;
        }

        @Override
        public boolean finishSpeech(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
            return true;
        }

        @Override
        public SupportLevel cancelSpeech(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
            return SupportLevel.SUPPORTED;
        }

        @Override
        public SupportLevel clearTextBuffer(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
            return SupportLevel.UNSUPPORTED;
        }

        @Override
        public void closeConnection(WebSocket webSocket) {
            closeCount++;
        }

        @Override
        public RealtimeTtsCapability capability(RealtimeTtsMessage request) {
            return new RealtimeTtsCapability();
        }

        @Override
        public WebSocketCallback createCallback(RealtimeTtsTaskListener listener, RealtimeTtsTaskContext taskContext, RealtimeTtsProperty property) {
            return new RaceCallback(listener, taskContext);
        }

        @Override
        public String getDescription() {
            return "race adaptor";
        }

        @Override
        public Class<RealtimeTtsProperty> getPropertyClass() {
            return RealtimeTtsProperty.class;
        }
    }

    private static class TestAdaptor implements RealtimeTtsAdaptor<RealtimeTtsProperty> {
        private int startConnectionCount;
        private int startSessionCount;
        private int closeCount;

        @Override
        public WebSocket startSpeech(String url, RealtimeTtsProperty property, RealtimeTtsMessage request, WebSocketCallback callback) {
            return startConnection(url, property, request, callback);
        }

        @Override
        public WebSocket startConnection(String url, RealtimeTtsProperty property, RealtimeTtsMessage request, WebSocketCallback callback) {
            startConnectionCount++;
            WebSocket webSocket = mock(WebSocket.class);
            if(callback instanceof TestCallback) {
                RealtimeTtsUpstreamConnection connection = ((TestCallback) callback).connection;
                connection.setWebSocket(webSocket);
                connection.setConnectionStarted(true);
            }
            return webSocket;
        }

        @Override
        public boolean startSession(WebSocket webSocket, RealtimeTtsTaskContext taskContext, WebSocketCallback callback) {
            startSessionCount++;
            taskContext.setUpstreamSessionStartSentMillis(1L);
            return true;
        }

        @Override
        public boolean inputText(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
            return true;
        }

        @Override
        public boolean finishSpeech(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
            return true;
        }

        @Override
        public SupportLevel cancelSpeech(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
            return SupportLevel.SUPPORTED;
        }

        @Override
        public SupportLevel clearTextBuffer(WebSocket webSocket, RealtimeTtsMessage request, WebSocketCallback callback) {
            return SupportLevel.UNSUPPORTED;
        }

        @Override
        public void closeConnection(WebSocket webSocket) {
            closeCount++;
        }

        @Override
        public RealtimeTtsCapability capability(RealtimeTtsMessage request) {
            return RealtimeTtsCapability.builder().connectionReuse(true).build();
        }

        @Override
        public WebSocketCallback createCallback(RealtimeTtsTaskListener listener, RealtimeTtsTaskContext taskContext, RealtimeTtsProperty property) {
            return new TestCallback();
        }

        @Override
        public String getDescription() {
            return "test adaptor";
        }

        @Override
        public Class<RealtimeTtsProperty> getPropertyClass() {
            return RealtimeTtsProperty.class;
        }
    }

    private static class TestCallback implements Callbacks.WebSocketCallback, RealtimeTtsConnectionAwareCallback {
        private RealtimeTtsUpstreamConnection connection;

        @Override
        public void setConnection(RealtimeTtsUpstreamConnection connection) {
            this.connection = connection;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        }

        @Override
        public boolean started() {
            return true;
        }
    }

    private static class RaceCallback implements Callbacks.WebSocketCallback {
        private final RealtimeTtsTaskListener listener;
        private final RealtimeTtsTaskContext taskContext;

        private RaceCallback(RealtimeTtsTaskListener listener, RealtimeTtsTaskContext taskContext) {
            this.listener = listener;
            this.taskContext = taskContext;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        }

        @Override
        public boolean started() {
            return false;
        }
    }
}
