package com.ke.bella.openapi.protocol.live.session;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.live.LiveSessionMessage;
import com.ke.bella.openapi.protocol.live.LiveSessionEventType;
import com.ke.bella.openapi.protocol.live.LiveSessionProperty;
import com.ke.bella.openapi.protocol.live.LiveSessionUsage;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.live.provider.LiveSessionAdaptor;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.WebSocket;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class LiveSessionHandler extends TextWebSocketHandler implements LiveSessionListener, SubProtocolCapable {
    private static final int MAX_MESSAGE_BYTES = 1024 * 1024;
    private static final int START_TIMEOUT_SECONDS = 10;
    private static final int CLOSE_TIMEOUT_SECONDS = 30;
    private static final int CLIENT_HEARTBEAT_INTERVAL_SECONDS = 10;
    private static final int CLIENT_HEARTBEAT_TIMEOUT_SECONDS = 30;
    private static final int BUSINESS_IDLE_TIMEOUT_MINUTES = 5;

    private final EndpointProcessData connectionProcessData;
    private final ApikeyInfo apikey;
    private final ChannelRouter router;
    private final AdaptorManager adaptorManager;
    private final LiveSessionAdmissionService admissionService;
    private final EndpointLogger logger;
    private final String sessionId = "live_" + UUID.randomUUID().toString().replace("-", "");
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean clientDisconnected = new AtomicBoolean();
    private final AtomicBoolean providerReady = new AtomicBoolean();
    private final AtomicBoolean finalUsageDelivered = new AtomicBoolean();
    private final LiveSessionUsage usage = new LiveSessionUsage();
    private final LiveSessionTrace trace = new LiveSessionTrace();
    private final AtomicLong lastClientPongNanos = new AtomicLong();
    private final AtomicLong lastBusinessActivityNanos = new AtomicLong();

    private volatile LiveSessionState state = LiveSessionState.AUTHENTICATED;
    private volatile WebSocketSession client;
    private volatile WebSocket upstream;
    private volatile LiveSessionAdaptor<LiveSessionProperty> adaptor;
    private volatile LiveSessionAdmissionService.Lease lease;
    private volatile EndpointProcessData processData;
    private volatile ScheduledFuture<?> startTimeout;
    private volatile ScheduledFuture<?> closeTimeout;
    private volatile ScheduledFuture<?> clientHeartbeat;
    private volatile ScheduledFuture<?> businessIdleTimeout;
    private volatile long startedAt;
    private volatile long clientCloseAt;

    public LiveSessionHandler(EndpointProcessData connectionProcessData, ApikeyInfo apikey, ChannelRouter router,
            AdaptorManager adaptorManager, LiveSessionAdmissionService admissionService, EndpointLogger logger) {
        this.connectionProcessData = new EndpointProcessData();
        BeanUtils.copyProperties(connectionProcessData, this.connectionProcessData);
        this.apikey = apikey;
        this.router = router;
        this.adaptorManager = adaptorManager;
        this.admissionService = admissionService;
        this.logger = logger;
    }

    @Override
    public List<String> getSubProtocols() {
        return Collections.singletonList(LiveSessionConstants.SUB_PROTOCOL);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        client = session;
        state = LiveSessionState.WAITING_START;
        lastClientPongNanos.set(System.nanoTime());
        scheduleClientHeartbeat();
        startTimeout = TaskExecutor.schedule(() -> fail("start_timeout", "等待 session.start 超时", CloseStatus.POLICY_VIOLATION),
                START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage textMessage) {
        try {
            restoreMessageContext();
            long receivedNanos = System.nanoTime();
            if(textMessage.getPayloadLength() > MAX_MESSAGE_BYTES) {
                fail("message_too_large", "Live 消息超过大小限制", CloseStatus.TOO_BIG_TO_PROCESS);
                return;
            }
            LiveSessionMessage message;
            try {
                message = JacksonUtils.deserialize(textMessage.getPayload(), LiveSessionMessage.class);
            } catch (RuntimeException exception) {
                fail("invalid_json", "Live 消息不是合法 JSON", CloseStatus.BAD_DATA);
                return;
            }
            LiveSessionEventType eventType = message == null ? null : message.eventType();
            if(eventType == null || !eventType.isClientEvent()) {
                fail("invalid_event", "未知或非法的客户端事件", CloseStatus.BAD_DATA);
                return;
            }
            if(state == LiveSessionState.CLOSING) {
                return;
            }
            if(!validatePayload(message, eventType)) {
                return;
            }
            trace.recordClientEvent(message, receivedNanos);
            if(state == LiveSessionState.WAITING_START) {
                if(eventType != LiveSessionEventType.SESSION_START && eventType != LiveSessionEventType.SETUP) {
                    fail("start_required", "首个事件必须是 session.start", CloseStatus.POLICY_VIOLATION);
                    return;
                }
                trace.markStarted(receivedNanos);
                start(message);
                return;
            }
            if(state != LiveSessionState.ACTIVE) {
                fail("invalid_state", "当前会话状态不接受该事件", CloseStatus.POLICY_VIOLATION);
                return;
            }
            recordBusinessActivity();
            if(eventType == LiveSessionEventType.SESSION_CLOSE) {
                state = LiveSessionState.CLOSING;
                clientCloseAt = DateTimeUtils.getCurrentMills();
                boolean sent = sendToProvider(message);
                log.debug("Live provider close requested: requestId={}, sessionId={}, sent={}, closeAt={}",
                        processData == null ? null : processData.getRequestId(), sessionId, sent, clientCloseAt);
                if(!sent) {
                    closeOnce("provider_close_send_failed", CloseStatus.NORMAL);
                    return;
                }
                scheduleCloseTimeout(CLOSE_TIMEOUT_SECONDS, "provider_close_timeout");
                log.debug("Live client remains connected, waiting provider close: requestId={}, sessionId={}, timeoutSeconds={}",
                        processData == null ? null : processData.getRequestId(), sessionId, CLOSE_TIMEOUT_SECONDS);
                return;
            }
            if(!sendToProvider(message)) {
                fail("provider_send_failed", "上游消息发送失败", CloseStatus.SERVER_ERROR);
            }
        } finally {
            EndpointContext.clearAll();
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        if(session == client && !closed.get()) {
            lastClientPongNanos.set(System.nanoTime());
        }
    }

    private void start(LiveSessionMessage start) {
        String model = start.resolveModel();
        if(StringUtils.isBlank(model)) {
            fail("model_required", "session.start 缺少 model", CloseStatus.BAD_DATA);
            return;
        }
        start.setModel(model);
        cancel(startTimeout);
        state = LiveSessionState.ROUTING;
        try {
            processData = restoreContext(start);
            lease = admissionService.acquire(apikey.getCode());
            List<ChannelDB> candidates = resumeCandidates(start);
            RuntimeException lastFailure = null;
            for(ChannelDB channel : candidates) {
                try {
                    EndpointContext.setEndpointData(channel);
                    LiveSessionAdaptor<LiveSessionProperty> selected = adaptorManager.getProtocolAdaptor(
                            LiveSessionConstants.ENDPOINT, channel.getProtocol(), LiveSessionAdaptor.class);
                    LiveSessionProperty property = JacksonUtils.deserialize(channel.getChannelInfo(), LiveSessionProperty.class);
                    state = LiveSessionState.CONNECTING_PROVIDER;
                    startTimeout = TaskExecutor.schedule(
                            () -> fail("provider_start_timeout", "等待上游 session.started 超时", CloseStatus.SERVER_ERROR),
                            START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    long connectStartedNanos = System.nanoTime();
                    WebSocket connection;
                    boolean connected = false;
                    try {
                        connection = selected.connect(channel.getUrl(), start, property, this);
                        connected = true;
                    } finally {
                        trace.recordProviderConnectCall(System.nanoTime() - connectStartedNanos, connected);
                    }
                    adaptor = selected;
                    upstream = connection;
                    if(closed.get()) {
                        selected.close(connection, "session_closed_during_connect");
                    }
                    return;
                } catch (RuntimeException exception) {
                    cancel(startTimeout);
                    lastFailure = exception;
                }
            }
            throw lastFailure == null ? new IllegalStateException("No Live channel connected") : lastFailure;
        } catch (RuntimeException exception) {
            fail("session_start_failed", exception.getMessage(), CloseStatus.SERVER_ERROR);
        }
    }

    private boolean validatePayload(LiveSessionMessage message, LiveSessionEventType eventType) {
        if(eventType == LiveSessionEventType.SESSION_CLOSE || eventType == LiveSessionEventType.SESSION_START
                || eventType == LiveSessionEventType.SETUP || eventType == LiveSessionEventType.SESSION_UPDATE) {
            return true;
        }
        if(eventType == LiveSessionEventType.INPUT_AUDIO_APPEND) {
            return validateBase64(message, "audio");
        }
        if(eventType == LiveSessionEventType.INPUT_VIDEO_APPEND) {
            return validateBase64(message, "video");
        }
        if(eventType == LiveSessionEventType.INPUT_TEXT_APPEND && message.payloadValue("text") == null) {
            fail("text_required", "文本事件缺少 text", CloseStatus.BAD_DATA);
            return false;
        }
        return true;
    }

    private boolean validateBase64(LiveSessionMessage message, String field) {
        Object value = message.payloadValue(field);
        if(value == null) {
            fail(field + "_required", "媒体事件缺少 " + field, CloseStatus.BAD_DATA);
            return false;
        }
        try {
            Base64.getDecoder().decode(String.valueOf(value));
            return true;
        } catch (IllegalArgumentException exception) {
            fail("invalid_base64", field + " 必须是 base64", CloseStatus.BAD_DATA);
            return false;
        }
    }

    private List<ChannelDB> resumeCandidates(LiveSessionMessage start) {
        Object resumeSessionId = start.payloadValue("resume_session_id");
        if(resumeSessionId != null) {
            throw new IllegalArgumentException("当前版本暂不支持 Live session resumption");
        }
        return router.routeCandidates(LiveSessionConstants.ENDPOINT, start.getModel(), apikey,
                processData.isMock(), false, null);
    }

    private EndpointProcessData restoreContext(LiveSessionMessage request) {
        EndpointProcessData restored = new EndpointProcessData();
        BeanUtils.copyProperties(connectionProcessData, restored);
        restored.setEndpoint(LiveSessionConstants.ENDPOINT);
        restored.setModel(request.getModel());
        restored.setSessionId(sessionId);
        restored.setRequest(request);
        restored.setRequestMillis(DateTimeUtils.getCurrentMills());
        restored.setRequestTime(DateTimeUtils.getCurrentSeconds());
        restored.setMetrics(new HashMap<>());
        EndpointContext.setProcessData(restored);
        EndpointContext.setApikey(apikey);
        return restored;
    }

    @Override
    public void onProviderMessage(LiveSessionMessage message) {
        handleProviderMessage(message, null);
    }

    @Override
    public void onProviderEvent(LiveProviderEvent event) {
        trace.recordProviderEvent(event);
        if(event != null && isProviderControlEvent(event.getType())) {
            log.debug("Live provider control event: requestId={}, sessionId={}, state={}, type={}, payloadCharacters={}, normalizedMessages={}",
                    processData == null ? null : processData.getRequestId(), sessionId, state, event.getType(),
                    event.getPayloadCharacters(), event.getNormalizedMessageCount());
        }
    }

    @Override
    public void onProviderClosing(int code, String reason) {
        log.debug("Live provider websocket closing: requestId={}, sessionId={}, state={}, code={}, reason={}",
                processData == null ? null : processData.getRequestId(), sessionId, state, code, reason);
    }

    @Override
    public void onProviderMessage(LiveSessionMessage message, LiveProviderEvent event) {
        handleProviderMessage(message, event);
    }

    private void handleProviderMessage(LiveSessionMessage message, LiveProviderEvent event) {
        if(message == null || closed.get()) {
            return;
        }
        trace.recordProviderMessage(message, event);
        LiveSessionEventType eventType = message.eventType();
        if(eventType == LiveSessionEventType.SESSION_STARTED) {
            providerStarted(message, event);
            return;
        }
        if(state == LiveSessionState.CONNECTING_PROVIDER) {
            if(eventType == LiveSessionEventType.ERROR) {
                message.setSessionId(sessionId);
                send(message, providerReceivedNanos(event));
                closeOnce("provider_start_failed", CloseStatus.SERVER_ERROR);
            } else {
                fail("provider_protocol_error", "上游在 session.started 前返回了业务事件", CloseStatus.SERVER_ERROR);
            }
            return;
        }
        if(state != LiveSessionState.ACTIVE && state != LiveSessionState.CLOSING) {
            return;
        }
        if(state == LiveSessionState.ACTIVE) {
            recordBusinessActivity();
        }
        message.setSessionId(sessionId);
        if(eventType == LiveSessionEventType.TOOL_CALL) {
            usage.setToolCalls((usage.getToolCalls() == null ? 0 : usage.getToolCalls()) + toolCallCount(message.getCall()));
        }
        if(message.getUsage() != null) {
            usage.mergeMaximum(message.getUsage());
        }
        if(eventType == LiveSessionEventType.SESSION_CLOSED) {
            normalizeFinalUsage();
            message.setUsage(usage);
            finalUsageDelivered.set(true);
        }
        send(message, providerReceivedNanos(event));
        if(eventType == LiveSessionEventType.SESSION_CLOSED) {
            closeOnce("provider_closed", CloseStatus.NORMAL);
        }
    }

    private void providerStarted(LiveSessionMessage message, LiveProviderEvent event) {
        if(!providerReady.compareAndSet(false, true)) {
            return;
        }
        if(state != LiveSessionState.CONNECTING_PROVIDER) {
            fail("provider_protocol_error", "上游 session.started 状态非法", CloseStatus.SERVER_ERROR);
            return;
        }
        String providerSessionId = message.getSessionId();
        if(StringUtils.isBlank(providerSessionId) && message.getSession() != null) {
            Object providerId = message.getSession().get("id");
            providerSessionId = providerId == null ? null : String.valueOf(providerId);
        }
        if(StringUtils.isNotBlank(providerSessionId)) {
            processData.getMetrics().put("provider_session_id", providerSessionId);
        }
        cancel(startTimeout);
        cancel(businessIdleTimeout);
        startedAt = DateTimeUtils.getCurrentMills();
        state = LiveSessionState.ACTIVE;
        lastBusinessActivityNanos.set(System.nanoTime());
        scheduleBusinessIdleTimeout();
        Map<String, Object> session = message.getSession() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(message.getSession());
        session.put("id", sessionId);
        session.putIfAbsent("model", processData.getModel());
        message.setSession(session);
        message.setSessionId(sessionId);
        message.setModel(processData.getModel());
        send(message, providerReceivedNanos(event));
    }

    @Override
    public void onProviderClosed(int code, String reason) {
        log.debug("Live provider websocket closed: requestId={}, sessionId={}, state={}, code={}, reason={}",
                processData == null ? null : processData.getRequestId(), sessionId, state, code, reason);
        if(state != LiveSessionState.CLOSING) {
            send(LiveSessionMessage.error(sessionId, "provider_closed", StringUtils.defaultIfBlank(reason, "上游连接已关闭")));
        }
        closeOnce("provider_closed_" + code, CloseStatus.NORMAL);
    }

    @Override
    public void onProviderFailure(Throwable throwable) {
        log.warn("Live provider websocket failure: requestId={}, sessionId={}, state={}, exception={}, message={}",
                processData == null ? null : processData.getRequestId(), sessionId, state,
                throwable == null ? null : throwable.getClass().getName(), throwable == null ? null : throwable.getMessage());
        if(state == LiveSessionState.CLOSING) {
            closeOnce("provider_closed_during_close", CloseStatus.NORMAL);
            return;
        }
        fail("provider_failure", throwable == null ? "上游连接失败" : throwable.getMessage(), CloseStatus.SERVER_ERROR);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if(session == client) {
            clientDisconnected.set(true);
        }
        if(session == client && state == LiveSessionState.CLOSING && !closed.get()) {
            cancel(clientHeartbeat);
            clientHeartbeat = null;
            return;
        }
        closeOnce("client_closed_" + status.getCode(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        if(session == client && state == LiveSessionState.CLOSING && !closed.get()) {
            clientDisconnected.set(true);
            cancel(clientHeartbeat);
            clientHeartbeat = null;
            return;
        }
        fail("transport_error", exception.getMessage(), CloseStatus.SERVER_ERROR);
    }

    private void fail(String code, String message, CloseStatus status) {
        send(LiveSessionMessage.error(sessionId, code, StringUtils.defaultIfBlank(message, code)));
        closeOnce(code, status);
    }

    private void send(LiveSessionMessage message) {
        send(message, 0);
    }

    private void send(LiveSessionMessage message, long providerReceivedNanos) {
        WebSocketSession current = client;
        if(current == null || !current.isOpen()) {
            trace.recordClientSendSkipped();
            return;
        }
        long sendStartedNanos = System.nanoTime();
        boolean success = false;
        try {
            synchronized (current) {
                current.sendMessage(new TextMessage(JacksonUtils.serialize(message)));
            }
            success = true;
        } catch (IOException exception) {
            log.warn("send Live message failed: {}", exception.getMessage());
        } finally {
            trace.recordClientSend(message, sendStartedNanos, System.nanoTime(), providerReceivedNanos, success);
        }
    }

    private void closeOnce(String reason, CloseStatus status) {
        if(!closed.compareAndSet(false, true)) {
            return;
        }
        LiveSessionState previousState = state;
        state = LiveSessionState.CLOSED;
        cancel(startTimeout);
        cancel(closeTimeout);
        cancel(clientHeartbeat);
        cancel(businessIdleTimeout);
        if(processData != null) {
            normalizeFinalUsage();
            long durationSeconds = usage.getSessionDurationSeconds() == null ? 0 : usage.getSessionDurationSeconds();
            processData.setUsage(usage);
            processData.setDuration(durationSeconds);
            processData.getMetrics().put("live_close_reason", reason);
            processData.getMetrics().put("final_usage_confirmed", usage.getFinalUsageConfirmed());
            if(previousState == LiveSessionState.CLOSING && finalUsageDelivered.compareAndSet(false, true)) {
                LiveSessionMessage closedMessage = LiveSessionMessage.event(LiveSessionEventType.SESSION_CLOSED, sessionId);
                closedMessage.setUsage(usage);
                send(closedMessage);
            }
            Object providerSessionId = processData.getMetrics().get("provider_session_id");
            processData.getMetrics().put("live_trace", trace.snapshot(processData.getRequestId(), processData.getBellaTraceId(), sessionId,
                    providerSessionId == null ? null : String.valueOf(providerSessionId)));
            logger.log(processData);
        }
        if(adaptor != null && upstream != null) {
            adaptor.close(upstream, reason);
        }
        if(lease != null) {
            lease.close();
        }
        disconnectClient(status);
    }

    private void normalizeFinalUsage() {
        long fallbackEndMillis = clientCloseAt > 0 ? clientCloseAt : DateTimeUtils.getCurrentMills();
        long fallbackDurationSeconds = startedAt == 0 ? 0 : Math.max(0, (fallbackEndMillis - startedAt) / 1000);
        boolean providerUsageConfirmed = Boolean.TRUE.equals(usage.getFinalUsageConfirmed())
                && usage.getSessionDurationSeconds() != null && usage.getSessionDurationSeconds() > 0;
        if(!providerUsageConfirmed) {
            usage.setSessionDurationSeconds(fallbackDurationSeconds);
        }
        usage.setFinalUsageConfirmed(providerUsageConfirmed);
    }

    private void cancel(ScheduledFuture<?> future) {
        if(future != null) {
            future.cancel(false);
        }
    }

    private void scheduleCloseTimeout(int delaySeconds, String reason) {
        cancel(closeTimeout);
        closeTimeout = TaskExecutor.schedule(() -> closeOnce(reason, CloseStatus.NORMAL), delaySeconds, TimeUnit.SECONDS);
    }

    private void disconnectClient(CloseStatus status) {
        cancel(clientHeartbeat);
        clientHeartbeat = null;
        if(!clientDisconnected.compareAndSet(false, true)) {
            return;
        }
        WebSocketSession current = client;
        if(current != null && current.isOpen()) {
            try {
                current.close(status);
            } catch (IOException exception) {
                log.debug("close Live client failed: {}", exception.getMessage());
            }
        }
    }

    private boolean isProviderControlEvent(String type) {
        return !"session.input_transcript.delta".equals(type)
                && !"session.output_transcript.delta".equals(type)
                && !"session.output_audio.delta".equals(type);
    }

    private void restoreMessageContext() {
        if(processData != null) {
            EndpointContext.setProcessData(processData);
            EndpointContext.setApikey(apikey);
        }
    }

    private void scheduleClientHeartbeat() {
        clientHeartbeat = TaskExecutor.schedule(this::checkClientHeartbeat,
                CLIENT_HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void recordBusinessActivity() {
        if(closed.get() || state != LiveSessionState.ACTIVE) {
            return;
        }
        lastBusinessActivityNanos.set(System.nanoTime());
        scheduleBusinessIdleTimeout();
    }

    private void scheduleBusinessIdleTimeout() {
        cancel(businessIdleTimeout);
        businessIdleTimeout = TaskExecutor.schedule(this::checkBusinessIdleTimeout,
                BUSINESS_IDLE_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    private void checkBusinessIdleTimeout() {
        if(closed.get() || state != LiveSessionState.ACTIVE) {
            return;
        }
        long idleNanos = System.nanoTime() - lastBusinessActivityNanos.get();
        long idleTimeoutNanos = TimeUnit.MINUTES.toNanos(BUSINESS_IDLE_TIMEOUT_MINUTES);
        if(idleNanos >= idleTimeoutNanos) {
            fail("idle_timeout", "Live 会话超过业务空闲时长", CloseStatus.SESSION_NOT_RELIABLE);
            return;
        }
        businessIdleTimeout = TaskExecutor.schedule(this::checkBusinessIdleTimeout,
                idleTimeoutNanos - idleNanos, TimeUnit.NANOSECONDS);
    }

    private void checkClientHeartbeat() {
        if(closed.get()) {
            return;
        }
        WebSocketSession current = client;
        if(current == null || !current.isOpen()) {
            if(state == LiveSessionState.CLOSING) {
                return;
            }
            closeOnce("client_heartbeat_closed", CloseStatus.SESSION_NOT_RELIABLE);
            return;
        }
        long elapsedNanos = System.nanoTime() - lastClientPongNanos.get();
        if(elapsedNanos >= TimeUnit.SECONDS.toNanos(CLIENT_HEARTBEAT_TIMEOUT_SECONDS)) {
            closeOnce("client_heartbeat_timeout", CloseStatus.SESSION_NOT_RELIABLE);
            return;
        }
        try {
            synchronized (current) {
                current.sendMessage(new PingMessage(ByteBuffer.allocate(0)));
            }
        } catch (IOException exception) {
            closeOnce("client_ping_failed", CloseStatus.SESSION_NOT_RELIABLE);
            return;
        }
        if(!closed.get()) {
            scheduleClientHeartbeat();
        }
    }

    private boolean sendToProvider(LiveSessionMessage message) {
        long sendStartedNanos = System.nanoTime();
        boolean success = false;
        try {
            success = adaptor != null && adaptor.send(upstream, message);
            return success;
        } catch (RuntimeException exception) {
            log.warn("send Live provider message failed: {}", exception.getMessage());
            return false;
        } finally {
            trace.recordUpstreamSend(System.nanoTime() - sendStartedNanos, success);
        }
    }

    private long providerReceivedNanos(LiveProviderEvent event) {
        return event == null ? 0 : event.getReceivedNanos();
    }

    private long toolCallCount(Object call) {
        if(call instanceof Map) {
            Object functionCalls = ((Map<?, ?>) call).get("functionCalls");
            if(functionCalls instanceof List) {
                return ((List<?>) functionCalls).size();
            }
        }
        return call == null ? 0 : 1;
    }
}
