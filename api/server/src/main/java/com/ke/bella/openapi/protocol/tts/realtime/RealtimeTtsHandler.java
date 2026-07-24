package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.WebSocket;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RealtimeTtsHandler extends TextWebSocketHandler implements RealtimeTtsTaskListener {
    private final EndpointProcessData connectionProcessData;
    private final ApikeyInfo apikey;
    private final AdaptorManager adaptorManager;
    private final RealtimeTtsAdmissionService admissionService;
    private final EndpointLogger logger;
    private final String sessionId = "sess_" + UUID.randomUUID().toString().replace("-", "");

    private volatile RealtimeTtsTaskContext activeTask;
    private volatile RealtimeTtsUpstreamConnection upstreamConnection;
    private volatile ScheduledFuture<?> upstreamIdleCloseFuture;
    private volatile RealtimeTtsClientSender sender;
    private final Object upstreamLock = new Object();

    public RealtimeTtsHandler(EndpointProcessData connectionProcessData, ApikeyInfo apikey,
            AdaptorManager adaptorManager, RealtimeTtsAdmissionService admissionService, EndpointLogger logger) {
        this.connectionProcessData = new EndpointProcessData();
        BeanUtils.copyProperties(connectionProcessData, this.connectionProcessData);
        this.apikey = apikey;
        this.adaptorManager = adaptorManager;
        this.admissionService = admissionService;
        this.logger = logger;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.sender = new RealtimeTtsClientSender(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        restoreContext(null);
        try {
            RealtimeTtsMessage request = JacksonUtils.deserialize(message.getPayload(), RealtimeTtsMessage.class);
            if(request == null || request.getHeader() == null || StringUtils.isBlank(request.getHeader().getName())) {
                sendFailed(null, RealtimeTtsMessage.STATUS_CLIENT_ERROR, "invalid_message", "Invalid realtime TTS message");
                return;
            }
            if(request.version() != RealtimeTtsMessage.PROTOCOL_VERSION) {
                sendFailed(request.taskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "unsupported_version", "Unsupported realtime TTS protocol version");
                return;
            }
            RealtimeTtsEventType eventType = RealtimeTtsEventType.fromString(request.getHeader().getName());
            if(eventType == null || !eventType.isClientEvent()) {
                sendFailed(request.taskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "unsupported_event", "Unsupported event: " + request.getHeader().getName());
                return;
            }
            switch (eventType) {
            case START_SPEECH:
                handleStartSpeech(request);
                break;
            case INPUT_TEXT:
                handleInputText(request);
                break;
            case FINISH_SPEECH:
                handleFinishSpeech(request);
                break;
            case CANCEL_SPEECH:
                handleCancelSpeech(request);
                break;
            case CLEAR_TEXT_BUFFER:
                handleClearTextBuffer(request);
                break;
            case PING:
                sender.sendText(RealtimeTtsMessage.pong(sessionId, currentTaskId()));
                break;
            default:
                sendFailed(request.taskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "unsupported_event", "Unsupported event");
            }
        } finally {
            EndpointContext.clearAll();
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        sendFailed(currentTaskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "binary_not_supported", "Realtime TTS endpoint does not accept binary input");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if(activeTask != null && activeTask.getState() != RealtimeTtsTaskContext.State.COMPLETED) {
            markCloseReason(activeTask, "client_close");
            finalizeTask(activeTask, RealtimeTtsMessage.cancelled(sessionId, activeTask.getTaskId(), "client_close"), "cancelled");
        } else {
            closeUpstream("client_close");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        if(activeTask != null && activeTask.getState() != RealtimeTtsTaskContext.State.COMPLETED) {
            markCloseReason(activeTask, "transport_error");
            finalizeTask(activeTask, RealtimeTtsMessage.failed(sessionId, activeTask.getTaskId(), RealtimeTtsMessage.STATUS_SERVER_ERROR,
                    exception.getMessage(), "transport_error", null, null), "failed");
        } else {
            closeUpstream("transport_error");
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void handleStartSpeech(RealtimeTtsMessage request) {
        long admissionStartMillis = DateTimeUtils.getCurrentMills();
        String model = request.getPayload() == null ? null : StringUtils.trimToNull(request.getPayload().getModel());
        if(StringUtils.isBlank(model)) {
            sendFailed(request.taskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "missing_model", "StartSpeech payload.model is required");
            return;
        }
        if(activeTask != null && activeTask.getState() != RealtimeTtsTaskContext.State.COMPLETED) {
            sendFailed(request.taskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "active_task_exists", "There is already an active speech task");
            return;
        }

        EndpointProcessData processData = restoreContext(request, model);
        RealtimeTtsTaskContext taskContext = null;
        try {
            ChannelDB channel = admissionService.admit(RealtimeTtsConstants.ENDPOINT, model, apikey, processData.isMock());
            taskContext = createTaskContext(request, processData);
            taskContext.getMetrics().put("admission_ms", DateTimeUtils.getCurrentMills() - admissionStartMillis);
            String protocol = processData.getProtocol();
            RealtimeTtsAdaptor selected = adaptorManager.getProtocolAdaptor(RealtimeTtsConstants.ENDPOINT, protocol, RealtimeTtsAdaptor.class);
            if(selected == null) {
                throw new BellaException.ChannelException(503, "Service Unavailable", "No realtime TTS adaptor for protocol: " + protocol);
            }
            RealtimeTtsProperty property = (RealtimeTtsProperty) JacksonUtils.deserialize(channel.getChannelInfo(), selected.getPropertyClass());
            taskContext.setFrozenPayload(freezePayload(request, property, model));
            taskContext.setCapability(selected.capability(request));
            taskContext.getMetrics().put("connection_reuse_enabled", connectionReuseEnabled(property, taskContext.getCapability()));
            RealtimeTtsUpstreamKey key = RealtimeTtsUpstreamKey.of(protocol, processData.getChannelCode(), processData.getForwardUrl(), property);
            long now = DateTimeUtils.getCurrentMills();
            RealtimeTtsUpstreamConnection reusableConnection = reusableConnection(key, property, taskContext.getCapability(), taskContext, now);
            this.activeTask = taskContext;
            if(reusableConnection != null) {
                taskContext.setCallback(reusableConnection.getCallback());
                taskContext.setUpstream(reusableConnection.getWebSocket());
                taskContext.setUpstreamConnection(reusableConnection);
                taskContext.getMetrics().put("upstream_reused", true);
                taskContext.getMetrics().put("upstream_ws_open_ms", 0);
                taskContext.getMetrics().put("connection_started_ms", 0);
                taskContext.getMetrics().put("upstream_connection_age_ms", now - reusableConnection.getOpenedAtMillis());
                if(!reusableConnection.getAdaptor().startSession(reusableConnection.getWebSocket(), taskContext, reusableConnection.getCallback())) {
                    markCloseReason(taskContext, "start_session_send_failed");
                    closeUpstream(reusableConnection, "start_session_send_failed");
                    onFailed(taskContext, "start_session_send_failed", "Failed to start realtime TTS provider session", null, null);
                }
            } else {
                Callbacks.WebSocketCallback callback = selected.createCallback(this, taskContext, property);
                RealtimeTtsUpstreamConnection connection = new RealtimeTtsUpstreamConnection(selected, callback, key, now);
                if(callback instanceof RealtimeTtsConnectionAwareCallback) {
                    ((RealtimeTtsConnectionAwareCallback) callback).setConnection(connection);
                }
                connection.register(taskContext);
                connection.setActiveProviderSessionId(taskContext.getProviderSessionId());
                taskContext.setCallback(callback);
                taskContext.setUpstreamConnection(connection);
                taskContext.setUpstreamConnectStartMillis(now);
                taskContext.getMetrics().put("upstream_reused", false);
                taskContext.getMetrics().put("upstream_connection_age_ms", 0);
                installUpstream(connection);
                WebSocket ws = selected.startConnection(processData.getForwardUrl(), property, request, callback);
                if(ws == null) {
                    throw new BellaException.ChannelException(503, "Service Unavailable", "Unable to connect realtime TTS provider");
                }
                connection.setWebSocket(ws);
                taskContext.setUpstream(ws);
                if(taskContext.getFinalized().get()) {
                    closeUpstream(connection, "task_finalized_before_upstream_assigned");
                    if(activeTask == taskContext) {
                        activeTask = null;
                    }
                }
            }
        } catch (Exception e) {
            if(taskContext != null && taskContext.getFinalized().get()) {
                if(activeTask == taskContext) {
                    activeTask = null;
                }
                EndpointContext.clearAll();
                return;
            }
            BellaException bellaException = BellaException.fromException(e);
            String failedTaskId = taskContext == null ? request.taskId() : taskContext.getTaskId();
            RealtimeTtsMessage failed = RealtimeTtsMessage.failed(sessionId, failedTaskId,
                    bellaException.getHttpCode() >= 500 ? RealtimeTtsMessage.STATUS_SERVER_ERROR : RealtimeTtsMessage.STATUS_CLIENT_ERROR,
                    bellaException.getMessage(), "speech_start_failed", null, null);
            sender.sendText(failed);
            if(taskContext != null && processData.getChannelCode() != null) {
                markCloseReason(taskContext, "speech_start_failed");
                closeUpstream(taskContext.getUpstreamConnection(), "speech_start_failed");
                finalizeTask(taskContext, failed, "failed");
            } else {
                if(activeTask == taskContext) {
                    activeTask = null;
                }
            }
            EndpointContext.clearAll();
        }
    }

    private RealtimeTtsTaskContext createTaskContext(RealtimeTtsMessage request, EndpointProcessData processData) {
        RealtimeTtsTaskContext taskContext = new RealtimeTtsTaskContext();
        taskContext.setSessionId(sessionId);
        taskContext.setTaskId(StringUtils.defaultIfBlank(request.taskId(), "task_" + UUID.randomUUID().toString().replace("-", "")));
        taskContext.setProviderSessionId(UUID.randomUUID().toString().replace("-", ""));
        taskContext.setStartRequest(request);
        taskContext.setProcessData(processData);
        taskContext.setStartMillis(DateTimeUtils.getCurrentMills());
        taskContext.getMetrics().put("input_characters", 0);
        return taskContext;
    }

    private void handleInputText(RealtimeTtsMessage request) {
        if(!requireActive(request, RealtimeTtsTaskContext.State.SPEECH_STARTED)) {
            return;
        }
        String text = request.getPayload() == null ? null : request.getPayload().getText();
        if(StringUtils.isEmpty(text)) {
            sendFailed(activeTask.getTaskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "empty_text", "InputText payload.text is required");
            return;
        }
        activeTask.setInputCharacters(activeTask.getInputCharacters() + text.length());
        activeTask.getMetrics().put("input_characters", activeTask.getInputCharacters());
        RealtimeTtsUpstreamConnection connection = connectionForTask(activeTask);
        if(connection == null || !connection.getAdaptor().inputText(connection.getWebSocket(), request, activeTask, connection.getCallback())) {
            markCloseReason(activeTask, "input_text_send_failed");
            closeUpstream(connection, "input_text_send_failed");
            onFailed(activeTask, "input_text_failed", "Failed to send text to realtime TTS provider", null, null);
        }
    }

    private void handleFinishSpeech(RealtimeTtsMessage request) {
        if(!requireActive(request, RealtimeTtsTaskContext.State.SPEECH_STARTED)) {
            return;
        }
        activeTask.setState(RealtimeTtsTaskContext.State.FINISHING);
        RealtimeTtsUpstreamConnection connection = connectionForTask(activeTask);
        if(connection == null || !connection.getAdaptor().finishSpeech(connection.getWebSocket(), request, activeTask, connection.getCallback())) {
            markCloseReason(activeTask, "finish_speech_send_failed");
            closeUpstream(connection, "finish_speech_send_failed");
            onFailed(activeTask, "finish_speech_failed", "Failed to finish realtime TTS provider session", null, null);
        }
    }

    private void handleCancelSpeech(RealtimeTtsMessage request) {
        if(activeTask == null || activeTask.getState() == RealtimeTtsTaskContext.State.COMPLETED) {
            sendFailed(request.taskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "no_active_task", "No active speech task");
            return;
        }
        if(!taskMatches(request)) {
            return;
        }
        RealtimeTtsUpstreamConnection connection = connectionForTask(activeTask);
        SupportLevel supportLevel = connection == null ? SupportLevel.CLOSE_CONNECTION
                : connection.getAdaptor().cancelSpeech(connection.getWebSocket(), request, activeTask, connection.getCallback());
        if(supportLevel == SupportLevel.UNSUPPORTED) {
            sendFailed(activeTask.getTaskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "cancel_unsupported", "CancelSpeech is unsupported by provider");
        } else if(supportLevel == SupportLevel.CLOSE_CONNECTION) {
            markCloseReason(activeTask, "cancelled");
            closeUpstream(connection, "cancelled");
            onCancelled(activeTask, cancelReason(request));
        }
    }

    private void handleClearTextBuffer(RealtimeTtsMessage request) {
        if(activeTask == null || activeTask.getState() == RealtimeTtsTaskContext.State.COMPLETED) {
            sendFailed(request.taskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "no_active_task", "No active speech task");
            return;
        }
        if(!taskMatches(request)) {
            return;
        }
        RealtimeTtsUpstreamConnection connection = connectionForTask(activeTask);
        SupportLevel supportLevel = connection == null ? SupportLevel.UNSUPPORTED
                : connection.getAdaptor().clearTextBuffer(connection.getWebSocket(), request, activeTask, connection.getCallback());
        if(supportLevel == SupportLevel.UNSUPPORTED) {
            sendFailed(activeTask.getTaskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "clear_text_buffer_unsupported",
                    "ClearTextBuffer is unsupported by provider");
        }
    }

    private boolean requireActive(RealtimeTtsMessage request, RealtimeTtsTaskContext.State expected) {
        if(activeTask == null || activeTask.getState() == RealtimeTtsTaskContext.State.COMPLETED) {
            sendFailed(request.taskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "speech_not_started", "StartSpeech is required before " + request.name());
            return false;
        }
        if(!taskMatches(request)) {
            return false;
        }
        if(activeTask.getState() != expected) {
            sendFailed(activeTask.getTaskId(), RealtimeTtsMessage.STATUS_CLIENT_ERROR, "invalid_state",
                    "Invalid state for " + request.name() + ": " + activeTask.getState());
            return false;
        }
        return true;
    }

    private boolean taskMatches(RealtimeTtsMessage request) {
        String requestTaskId = request.taskId();
        if(StringUtils.isNotBlank(requestTaskId) && !requestTaskId.equals(activeTask.getTaskId())) {
            sendFailed(requestTaskId, RealtimeTtsMessage.STATUS_CLIENT_ERROR, "invalid_task_id", "Invalid task_id");
            return false;
        }
        return true;
    }

    @Override
    public void onSpeechStarted(RealtimeTtsTaskContext taskContext) {
        if(activeTask != taskContext) {
            return;
        }
        taskContext.setState(RealtimeTtsTaskContext.State.SPEECH_STARTED);
        if(!sender.sendText(RealtimeTtsMessage.started(sessionId, taskContext.getTaskId(), taskContext.getCapability()))) {
            finalizeClientSendFailure(taskContext);
        }
    }

    @Override
    public void onAudio(RealtimeTtsTaskContext taskContext, byte[] audio, Long durationMs) {
        if(activeTask != taskContext || audio == null || audio.length == 0) {
            return;
        }
        long now = DateTimeUtils.getCurrentMills();
        if(taskContext.getFirstAudioMillis() == null) {
            taskContext.setFirstAudioMillis(now);
            taskContext.getMetrics().put("ttft_ms", now - taskContext.getStartMillis());
        }
        taskContext.setAudioFrames(taskContext.getAudioFrames() + 1);
        taskContext.setAudioBytes(taskContext.getAudioBytes() + audio.length);
        if(durationMs != null) {
            taskContext.setAudioDurationMs(taskContext.getAudioDurationMs() + durationMs);
        }
        RealtimeTtsMessage delta = RealtimeTtsMessage.audioDelta(sessionId, taskContext.getTaskId(), taskContext.nextSequence(),
                "aud_" + UUID.randomUUID().toString().replace("-", ""), taskContext.getFrozenPayload(), audio.length, durationMs, false);
        if(!sender.sendAudioDelta(delta, audio)) {
            finalizeClientSendFailure(taskContext);
        }
    }

    @Override
    public void onTimestamp(RealtimeTtsTaskContext taskContext, RealtimeTtsPayload payload) {
        if(activeTask != taskContext) {
            return;
        }
        if(!sender.sendText(RealtimeTtsMessage.response(RealtimeTtsEventType.SPEECH_TIMESTAMP, sessionId, taskContext.getTaskId(),
                taskContext.nextSequence(), RealtimeTtsMessage.STATUS_SUCCESS, "Success", payload))) {
            finalizeClientSendFailure(taskContext);
        }
    }

    @Override
    public void onCompleted(RealtimeTtsTaskContext taskContext, Map<String, Object> providerUsage) {
        if(activeTask != taskContext) {
            return;
        }
        Map<String, Object> metrics = completeMetrics(taskContext);
        Map<String, Object> usage = usage(taskContext, providerUsage);
        RealtimeTtsMessage completed = RealtimeTtsMessage.completed(sessionId, taskContext.getTaskId(), taskContext.getAudioDurationMs(), usage, metrics);
        sender.sendText(completed);
        finalizeTask(taskContext, completed, "completed");
    }

    @Override
    public void onCancelled(RealtimeTtsTaskContext taskContext, String reason) {
        if(activeTask != taskContext) {
            return;
        }
        markCloseReason(taskContext, "cancelled");
        RealtimeTtsMessage cancelled = RealtimeTtsMessage.cancelled(sessionId, taskContext.getTaskId(), StringUtils.defaultIfBlank(reason, "cancelled"));
        sender.sendText(cancelled);
        finalizeTask(taskContext, cancelled, "cancelled");
    }

    @Override
    public void onFailed(RealtimeTtsTaskContext taskContext, String code, String message, String providerCode, String providerMessage) {
        if(activeTask != taskContext) {
            return;
        }
        markCloseReason(taskContext, closeReasonForFailure(code));
        taskContext.getMetrics().put("failure_code", code);
        RealtimeTtsMessage failed = RealtimeTtsMessage.failed(sessionId, taskContext.getTaskId(), RealtimeTtsMessage.STATUS_SERVER_ERROR,
                message, code, providerCode, providerMessage);
        sender.sendText(failed);
        finalizeTask(taskContext, failed, "failed");
    }

    private void finalizeTask(RealtimeTtsTaskContext taskContext, RealtimeTtsMessage terminalMessage, String status) {
        if(!taskContext.getFinalized().compareAndSet(false, true)) {
            return;
        }
        taskContext.setState(RealtimeTtsTaskContext.State.COMPLETED);
        RealtimeTtsUpstreamConnection connection = connectionForTask(taskContext);
        if(connection != null) {
            connection.clearActiveSession(taskContext);
            connection.unregister(taskContext);
            connection.markUsed(DateTimeUtils.getCurrentMills());
        }
        if(canKeepUpstreamAfterTerminal(connection, taskContext, status)) {
            String keepReason = taskContext.isProviderSessionFailure() ? "kept_after_session_failure" : "kept_for_reuse";
            markCloseReason(taskContext, keepReason);
            scheduleUpstreamIdleClose(connection);
        } else {
            String reason = StringUtils.defaultIfBlank((String) taskContext.getMetrics().get("upstream_close_reason"),
                    "completed".equals(status) ? "reuse_disabled" : status);
            markCloseReason(taskContext, reason);
            if(connection != null && taskContext.getUpstream() != null && taskContext.getUpstream() == connection.getWebSocket()) {
                closeUpstream(connection, reason);
            }
        }
        EndpointProcessData processData = taskContext.getProcessData();
        processData.setMetrics(completeMetrics(taskContext));
        processData.setUsage(usage(taskContext, terminalMessage.getPayload() == null ? null : terminalMessage.getPayload().getUsage()));
        processData.setResponseRaw(JacksonUtils.serialize(terminalMessage));
        processData.setDuration((DateTimeUtils.getCurrentMills() - taskContext.getStartMillis()) / 1000);
        processData.getMetrics().put("status", status);
        logger.log(processData);
        if(activeTask == taskContext) {
            activeTask = null;
        }
    }

    private void finalizeClientSendFailure(RealtimeTtsTaskContext taskContext) {
        if(activeTask != taskContext) {
            return;
        }
        markCloseReason(taskContext, "client_send_failed");
        RealtimeTtsMessage failed = RealtimeTtsMessage.failed(sessionId, taskContext.getTaskId(), RealtimeTtsMessage.STATUS_SERVER_ERROR,
                "Client websocket is not writable", "client_send_failed", null, null);
        finalizeTask(taskContext, failed, "failed");
    }

    private Map<String, Object> completeMetrics(RealtimeTtsTaskContext taskContext) {
        Map<String, Object> metrics = new HashMap<>(taskContext.getMetrics());
        metrics.put("ttlt_ms", DateTimeUtils.getCurrentMills() - taskContext.getStartMillis());
        metrics.put("audio_frames", taskContext.getAudioFrames());
        metrics.put("audio_bytes", taskContext.getAudioBytes());
        metrics.put("audio_duration_ms", taskContext.getAudioDurationMs());
        metrics.put("input_characters", taskContext.getInputCharacters());
        return metrics;
    }

    private Map<String, Object> usage(RealtimeTtsTaskContext taskContext, Map<String, Object> providerUsage) {
        Map<String, Object> usage = providerUsage == null ? new HashMap<>() : new HashMap<>(providerUsage);
        Object input = usage.get("input_characters");
        if(!(input instanceof Number) || ((Number) input).intValue() <= 0) {
            usage.put("input_characters", taskContext.getInputCharacters());
        }
        if(!usage.containsKey("provider_text_units")) {
            usage.put("provider_text_units", usage.get("input_characters"));
        }
        return usage;
    }

    private EndpointProcessData restoreContext(RealtimeTtsMessage request) {
        return restoreContext(request, request == null || request.getPayload() == null ? null : StringUtils.trimToNull(request.getPayload().getModel()));
    }

    private EndpointProcessData restoreContext(RealtimeTtsMessage request, String model) {
        EndpointProcessData processData = new EndpointProcessData();
        BeanUtils.copyProperties(connectionProcessData, processData);
        processData.setEndpoint(RealtimeTtsConstants.ENDPOINT);
        processData.setModel(model);
        processData.setRequest(request);
        processData.setRequestMillis(DateTimeUtils.getCurrentMills());
        processData.setRequestTime(DateTimeUtils.getCurrentSeconds());
        processData.setRequestId(UUID.randomUUID().toString());
        processData.setMetrics(new HashMap<>());
        EndpointContext.setProcessData(processData);
        EndpointContext.setApikey(apikey);
        return processData;
    }

    private RealtimeTtsPayload freezePayload(RealtimeTtsMessage request, RealtimeTtsProperty property, String model) {
        RealtimeTtsPayload payload = request.getPayload() == null ? new RealtimeTtsPayload() : request.getPayload();
        return RealtimeTtsPayload.builder()
                .model(model)
                .voice(StringUtils.defaultIfBlank(payload.getVoice(), property.getDefaultVoice()))
                .format(StringUtils.defaultIfBlank(payload.getFormat(), property.getDefaultContentType()))
                .sampleRate(payload.getSampleRate() == null ? property.getDefaultSampleRate() : payload.getSampleRate())
                .channels(payload.getChannels() == null ? 1 : payload.getChannels())
                .encoding(StringUtils.defaultIfBlank(payload.getEncoding(), "s16le"))
                .speed(payload.getSpeed() == null ? 1.0 : payload.getSpeed())
                .volume(payload.getVolume() == null ? 1.0 : payload.getVolume())
                .pitch(payload.getPitch() == null ? 1.0 : payload.getPitch())
                .language(payload.getLanguage())
                .textType(StringUtils.defaultIfBlank(payload.getTextType(), "plain"))
                .enableTimestamp(Boolean.TRUE.equals(payload.getEnableTimestamp()))
                .extraBody(payload.getExtraBody())
                .build();
    }

    private String cancelReason(RealtimeTtsMessage request) {
        return request.getPayload() == null ? null : request.getPayload().getReason();
    }

    private void sendFailed(String taskId, int status, String code, String message) {
        if(sender == null) {
            return;
        }
        sender.sendText(RealtimeTtsMessage.failed(sessionId, taskId, status, message, code, null, null));
    }

    private String currentTaskId() {
        return activeTask == null ? null : activeTask.getTaskId();
    }

    private RealtimeTtsUpstreamConnection reusableConnection(RealtimeTtsUpstreamKey key, RealtimeTtsProperty property,
            RealtimeTtsCapability capability, RealtimeTtsTaskContext taskContext, long now) {
        RealtimeTtsUpstreamConnection rejected = null;
        String rejectionReason = null;
        synchronized(upstreamLock) {
            RealtimeTtsUpstreamConnection connection = upstreamConnection;
            if(connection == null) {
                return null;
            }
            if(connection.canReuseFor(key, property, capability, now)) {
                cancelUpstreamIdleCloseLocked();
                connection.register(taskContext);
                connection.setActiveProviderSessionId(taskContext.getProviderSessionId());
                return connection;
            }
            rejected = connection;
            rejectionReason = connection.rejectionReason(key, property, capability, now);
        }
        closeUpstream(rejected, rejectionReason);
        return null;
    }

    private boolean canKeepUpstreamAfterTerminal(RealtimeTtsUpstreamConnection connection, RealtimeTtsTaskContext taskContext, String status) {
        boolean reusableTerminal = "completed".equals(status) || ("failed".equals(status) && taskContext.isProviderSessionFailure());
        return connection != null
                && reusableTerminal
                && upstreamConnection == connection
                && connection.getWebSocket() != null
                && taskContext.getUpstream() == connection.getWebSocket()
                && connection.isHealthy()
                && connection.isConnectionStarted()
                && Boolean.TRUE.equals(taskContext.getMetrics().get("connection_reuse_enabled"));
    }

    private boolean connectionReuseEnabled(RealtimeTtsProperty property, RealtimeTtsCapability capability) {
        return Boolean.TRUE.equals(property == null ? Boolean.TRUE : property.getConnectionReuse())
                && capability != null && Boolean.TRUE.equals(capability.getConnectionReuse());
    }

    private void markCloseReason(RealtimeTtsTaskContext taskContext, String reason) {
        if(taskContext != null && StringUtils.isNotBlank(reason)) {
            taskContext.getMetrics().put("upstream_close_reason", reason);
        }
        RealtimeTtsUpstreamConnection connection = taskContext == null ? upstreamConnection : connectionForTask(taskContext);
        if(connection != null && StringUtils.isNotBlank(reason)) {
            connection.setCloseReason(reason);
        }
    }

    private String closeReasonForFailure(String code) {
        if("provider_connection_closed".equals(code)) {
            return "provider_connection_closed";
        }
        if("provider_failure".equals(code) || "provider_error".equals(code)) {
            return "provider_failure";
        }
        if("input_text_failed".equals(code)) {
            return "input_text_send_failed";
        }
        if("finish_speech_failed".equals(code)) {
            return "finish_speech_send_failed";
        }
        if("start_session_send_failed".equals(code)) {
            return "start_session_send_failed";
        }
        if("client_send_failed".equals(code)) {
            return "client_send_failed";
        }
        if("transport_error".equals(code)) {
            return "transport_error";
        }
        return "provider_failure";
    }

    private RealtimeTtsUpstreamConnection connectionForTask(RealtimeTtsTaskContext taskContext) {
        if(taskContext == null) {
            return null;
        }
        RealtimeTtsUpstreamConnection connection = taskContext.getUpstreamConnection();
        if(connection != null) {
            return connection;
        }
        RealtimeTtsUpstreamConnection current = upstreamConnection;
        return current != null && taskContext.getUpstream() == current.getWebSocket() ? current : null;
    }

    private void installUpstream(RealtimeTtsUpstreamConnection connection) {
        synchronized(upstreamLock) {
            cancelUpstreamIdleCloseLocked();
            upstreamConnection = connection;
        }
    }

    private void scheduleUpstreamIdleClose(RealtimeTtsUpstreamConnection connection) {
        synchronized(upstreamLock) {
            if(upstreamConnection != connection || connection.hasActiveSession() || !connection.isHealthy()) {
                return;
            }
            cancelUpstreamIdleCloseLocked();
            long lastUsedAtMillis = connection.getLastUsedAtMillis();
            upstreamIdleCloseFuture = TaskExecutor.schedule(
                    () -> closeIdleUpstream(connection, lastUsedAtMillis),
                    RealtimeTtsUpstreamConnection.DEFAULT_IDLE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
        }
    }

    private void closeIdleUpstream(RealtimeTtsUpstreamConnection connection, long expectedLastUsedAtMillis) {
        boolean detached;
        synchronized(upstreamLock) {
            long now = DateTimeUtils.getCurrentMills();
            if(upstreamConnection != connection
                    || connection.hasActiveSession()
                    || connection.getLastUsedAtMillis() != expectedLastUsedAtMillis
                    || now - expectedLastUsedAtMillis < RealtimeTtsUpstreamConnection.DEFAULT_IDLE_TIMEOUT_MS) {
                return;
            }
            detached = detachUpstreamLocked(connection, "idle_timeout");
        }
        if(detached) {
            closeDetachedUpstream(connection);
        }
    }

    private void cancelUpstreamIdleCloseLocked() {
        ScheduledFuture<?> future = upstreamIdleCloseFuture;
        upstreamIdleCloseFuture = null;
        if(future != null) {
            future.cancel(false);
        }
    }

    private void closeUpstream(String reason) {
        closeUpstream(upstreamConnection, reason);
    }

    private void closeUpstream(RealtimeTtsUpstreamConnection expectedConnection, String reason) {
        if(expectedConnection == null) {
            return;
        }
        boolean detached;
        synchronized(upstreamLock) {
            detached = detachUpstreamLocked(expectedConnection, reason);
        }
        if(detached) {
            closeDetachedUpstream(expectedConnection);
        }
    }

    private boolean detachUpstreamLocked(RealtimeTtsUpstreamConnection expectedConnection, String reason) {
        if(upstreamConnection != expectedConnection) {
            return false;
        }
        upstreamConnection = null;
        cancelUpstreamIdleCloseLocked();
        expectedConnection.setHealthy(false);
        expectedConnection.setCloseReason(reason);
        return true;
    }

    private void closeDetachedUpstream(RealtimeTtsUpstreamConnection connection) {
        WebSocket ws = connection.getWebSocket();
        RealtimeTtsAdaptor selected = connection.getAdaptor();
        if(ws != null && selected != null) {
            try {
                selected.closeConnection(ws);
            } catch (Exception ignored) {
                // Best-effort cleanup. The local reference must still be released.
            }
        }
    }
}
