package com.ke.bella.openapi.protocol.realtime;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.Callbacks.WebSocketCallback;
import com.ke.bella.openapi.protocol.asr.TencentV2StreamAsrCallback;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.metrics.VoiceWebSocketPrometheusRecorder;
import com.ke.bella.openapi.service.CredentialSession;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.WebSocket;

/**
 * 实时语音WebSocket处理器
 */

public class RealTimeHandler extends TextWebSocketHandler implements SubProtocolCapable {

    private final String url;
    private final AsrProperty property;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;
    private final RealTimeAdaptor<AsrProperty> adaptor;
    private final CredentialSession credentialSession;
    private static final Logger LOGGER = LoggerFactory.getLogger(RealTimeHandler.class);
    private String taskId;
    // 与ASR服务的WebSocket连接
    private WebSocket ws;
    private WebSocketCallback callback;
    private final VoiceWebSocketPrometheusRecorder webSocketPrometheusRecorder;
    private boolean clientWsClosed;
    private boolean upstreamWsClosed;

    @Override
    public List<String> getSubProtocols() {
        return Collections.singletonList("bella-realtime");
    }

    public RealTimeHandler(String url, AsrProperty property, EndpointProcessData processData, EndpointLogger logger,
            RealTimeAdaptor<AsrProperty> adaptor) {
        this(url, property, processData, logger, adaptor, null, null);
    }

    public RealTimeHandler(String url, AsrProperty property, EndpointProcessData processData, EndpointLogger logger,
            RealTimeAdaptor<AsrProperty> adaptor, CredentialSession credentialSession,
            VoiceWebSocketPrometheusRecorder webSocketPrometheusRecorder) {
        this.url = url;
        this.property = property;
        this.processData = processData;
        this.logger = logger;
        this.adaptor = adaptor;
        this.webSocketPrometheusRecorder = webSocketPrometheusRecorder;
        this.credentialSession = credentialSession == null ? CredentialSession.NOOP : credentialSession;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if(webSocketPrometheusRecorder != null) {
            webSocketPrometheusRecorder.opened(processData, "client");
        }
        LOGGER.debug("客户端WebSocket连接已建立: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            if(payload.equals("ping")) {
                session.sendMessage(new TextMessage("pong"));
                return;
            }
            // 先解析基本消息结构，获取消息类型
            RealTimeMessage realTimeMessage = JacksonUtils.deserialize(payload, RealTimeMessage.class);
            if(realTimeMessage == null || realTimeMessage.getHeader() == null || realTimeMessage.getHeader().getName() == null) {
                terminateSession(session, 40000000, "消息格式无效");
                return;
            }

            RealTimeEventType eventType = RealTimeEventType.fromString(realTimeMessage.getHeader().getName());
            switch (eventType) {
            case START_TRANSCRIPTION:
                handleStartTranscription(session, realTimeMessage);
                break;
            case STOP_TRANSCRIPTION:
                handleStopTranscription(session, realTimeMessage);
                break;
            default:
                LOGGER.warn("不支持的事件类型: " + realTimeMessage.getHeader().getName());
                terminateSession(session, 40000000, "不支持的事件类型: " + realTimeMessage.getHeader().getName());
                break;
            }
        } catch (Exception e) {
            LOGGER.warn("处理文本消息时出错: {}", e.getMessage());
            LOGGER.warn(e.getMessage(), e);
            BellaException exception = BellaException.fromException(e);
            int status = exception.getHttpCode() < 500 ? 40000000 : 50000000;
            terminateSession(session, status, exception.getMessage());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            byte[] audioData = message.getPayload().array();

            if(taskId == null) {
                sendErrorResponse(session, 40000000, "未开始转录任务，请先发送StartTranscription指令");
                return;
            }

            if(ws == null) {
                sendErrorResponse(session, 50000000, "未连接到ASR服务");
                return;
            }

            // 发送音频数据到第三方服务
            boolean success = adaptor.sendAudioData(ws, audioData, callback);
            if(!success) {
                // 发送失败，尝试发送空字节心跳检测连接状态
                LOGGER.info("音频数据发送失败，尝试心跳检测连接状态");
                boolean heartbeatSuccess = adaptor.sendAudioData(ws, new byte[0], callback);
                if(!heartbeatSuccess) {
                    // 心跳也失败，确认连接断开，清理状态并关闭客户端连接
                    LOGGER.warn("心跳检测失败，ASR服务连接已断开，关闭客户端连接");
                    closeUpstream("heartbeat_failed");
                    ws = null;
                    taskId = null;
                    sendErrorResponse(session, 50000000, "ASR服务连接已断开");
                    try {
                        session.close();
                    } catch (Exception e) {
                        LOGGER.warn("关闭客户端连接时出错: {}", e.getMessage());
                    }
                } else {
                    // 心跳成功但音频数据发送失败，可能是临时问题
                    sendErrorResponse(session, 50000000, "发送音频数据失败");
                }
            }

        } catch (Exception e) {
            LOGGER.warn("处理二进制消息时出错: {}", e.getMessage());
            terminateSession(session, 50000000, "处理音频数据时出错: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LOGGER.debug("客户端WebSocket连接已关闭, status: {}", status);
        recordClientClosed("client_close", String.valueOf(status.getCode()));

        // 关闭与第三方ws服务的连接
        closeUpstream("client_close");

        taskId = null;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOGGER.warn("WebSocket传输错误: {}", exception.getMessage());
        recordClientClosed("transport_error", null);

        // 关闭与第三方服务的连接
        closeUpstream("transport_error");

        taskId = null;
    }

    private void handleStartTranscription(WebSocketSession session, RealTimeMessage request) throws IOException {
        if(taskId != null) {
            sendErrorResponse(session, 40000000, "已有转录任务正在进行");
            return;
        }

        try {
            credentialSession.refreshForNewTask(processData.getEndpoint(), processData);
            request.setApikey(processData.getApikey());

            // 获取或生成任务ID
            taskId = request.getHeader().getTaskId();
            if(taskId == null) {
                taskId = UUID.randomUUID().toString();
            }

            if("/v1/audio/asr/stream".equals(processData.getEndpoint())) {
                request.getHeader().setTaskId(taskId);
                processData.setRequest(request);
            }

            // 创建回调处理器
            callback = adaptor.createCallback(webSocketSender(session, processData), processData, logger, taskId, request, property);

            // 创建与第三方服务的连接并开始转录
            ws = adaptor.startTranscription(url, property, request, callback);

            if(!session.isOpen() || taskId == null) {
                if(ws != null) {
                    adaptor.closeConnection(ws);
                    ws = null;
                }
                return;
            }
            if(ws == null) {
                String failedTaskId = taskId;
                rollbackStart();
                sendErrorResponse(session, 50000000, "无法连接到ASR服务", failedTaskId);
                return;
            }
            upstreamWsClosed = false;
            if(webSocketPrometheusRecorder != null) {
                webSocketPrometheusRecorder.opened(processData, "upstream");
            }

            // 发送TranscriptionStarted响应
            sendTranscriptionStartedResponse(session, taskId);
        } catch (Exception e) {
            String failedTaskId = taskId;
            rollbackStart();
            BellaException exception = BellaException.fromException(e);
            int status = exception.getHttpCode() < 500 ? 40000000 : 50000000;
            sendErrorResponse(session, status, exception.getMessage(), failedTaskId);
        }
    }

    private void rollbackStart() {
        try {
            closeUpstream("start_failed");
        } catch (RuntimeException e) {
            LOGGER.warn("回滚ASR启动状态时关闭上游连接失败: {}", e.getMessage(), e);
        } finally {
            ws = null;
            callback = null;
            taskId = null;
        }
    }

    private void handleStopTranscription(WebSocketSession session, RealTimeMessage request) {
        if(taskId == null || ws == null) {
            sendErrorResponse(session, 40000000, "没有正在进行的转录任务");
            return;
        }

        String msgTaskId = request.getHeader().getTaskId();
        if(msgTaskId != null && !msgTaskId.equals(taskId)) {
            sendErrorResponse(session, 40000000, "无效的任务ID");
            return;
        }

        // 发送结束转录指令
        boolean success = adaptor.stopTranscription(ws, request, callback);

        if(!success) {
            terminateSession(session, 50000000, "无法停止转录任务");
        }
    }

    private void closeUpstream(String reason) {
        if(ws == null) {
            return;
        }
        recordUpstreamClosed(reason);
        adaptor.closeConnection(ws);
        ws = null;
    }

    private void terminateSession(WebSocketSession session, int status, String errorMessage) {
        try {
            RealTimeMessage response = sendErrorResponse(session, status, errorMessage);
            if(response != null) {
                processData.setResponse(response);
            }
        } finally {
            closeUpstream("error");
            taskId = null;
            try {
                if(session.isOpen()) {
                    session.close();
                }
            } catch (IOException e) {
                LOGGER.warn("关闭客户端连接时出错: {}", e.getMessage());
            }
        }
    }

    private void recordClientClosed(String reason, String closeCode) {
        if(webSocketPrometheusRecorder == null || clientWsClosed) {
            return;
        }
        clientWsClosed = true;
        webSocketPrometheusRecorder.closed(processData, "client", reason, closeCode);
    }

    private void recordUpstreamClosed(String reason) {
        if(webSocketPrometheusRecorder == null || upstreamWsClosed) {
            return;
        }
        upstreamWsClosed = true;
        webSocketPrometheusRecorder.closed(processData, "upstream", reason, null);
    }

    private void sendTranscriptionStartedResponse(WebSocketSession session, String taskId) throws IOException {
        RealTimeMessage response = startedResponse(taskId, callback);
        session.sendMessage(new TextMessage(JacksonUtils.serialize(response)));
    }

    static RealTimeMessage startedResponse(String taskId, WebSocketCallback callback) {
        RealTimeMessage response = RealTimeMessage.startedResponse(taskId);
        Map<String, Object> additions = new HashMap<>();
        if(callback != null) {
            if(StringUtils.isNotBlank(callback.getUpstreamLogId())) {
                additions.put("logid", callback.getUpstreamLogId());
            }
            if(StringUtils.isNotBlank(callback.getUpstreamConnectId())) {
                additions.put("connect_id", callback.getUpstreamConnectId());
            }
        }
        if(callback instanceof TencentV2StreamAsrCallback) {
            String speakerContextId = ((TencentV2StreamAsrCallback) callback).getSpeakerContextId();
            if(speakerContextId != null) {
                additions.put("speaker_context_id", speakerContextId);
            }
        }
        if(!additions.isEmpty()) {
            RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
            payload.setAdditions(additions);
            response.setPayload(payload);
        }
        return response;
    }

    private RealTimeMessage sendErrorResponse(WebSocketSession session, int status, String errorMessage) {
        return sendErrorResponse(session, status, errorMessage, taskId);
    }

    private RealTimeMessage sendErrorResponse(WebSocketSession session, int status, String errorMessage, String responseTaskId) {
        if(!session.isOpen()) {
            return null;
        }
        int httpCode = status >= 50000000 ? 500 : 400;
        RealTimeMessage response = RealTimeMessage.errorResponse(httpCode, status, errorMessage, responseTaskId);
        try {
            session.sendMessage(new TextMessage(JacksonUtils.serialize(response)));
        } catch (IOException e) {
            LOGGER.warn("发送错误响应失败: {}", e.getMessage());
        }
        return response;
    }

    private Callbacks.Sender webSocketSender(WebSocketSession session, EndpointProcessData processData) {
        final TranscriptionDurationAccumulator duration = new TranscriptionDurationAccumulator();
        return new Callbacks.Sender() {
            @Override
            public void send(String text) {
                try {
                    if(session.isOpen()) {
                        session.sendMessage(new TextMessage(text));
                    } else {
                        LOGGER.warn("client session is closed");
                    }
                } catch (IOException e) {
                    LOGGER.warn(e.getMessage(), e);
                    onError(e);
                    return;
                }
                RealTimeMessage message;
                try {
                    message = JacksonUtils.deserialize(text, RealTimeMessage.class);
                } catch (Exception e) {
                    onError(e);
                    return;
                }
                if(message == null || message.getHeader() == null || message.getHeader().getName() == null) {
                    onError(new IllegalArgumentException("实时响应格式无效"));
                    return;
                }
                if(message.getHeader().getName().equals(RealTimeEventType.SENTENCE_END.getValue())) {
                    duration.record(message.getPayload());
                }
            }

            @Override
            public void send(byte[] bytes) {
                try {
                    if(session.isOpen()) {
                        session.sendMessage(new BinaryMessage(bytes));
                    } else {
                        LOGGER.warn("client session is closed");
                    }
                } catch (IOException e) {
                    LOGGER.warn(e.getMessage(), e);
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                BellaException exception = BellaException.fromException(e);
                terminateSession(session, exception.getHttpCode() < 500 ? 40000000 : 50000000, exception.getMessage());
            }

            @Override
            public void close() {
                try {
                    processData.setTranscriptionDuration(duration.seconds());
                    closeUpstream("callback_close");
                    taskId = null;
                    if(session.isOpen()) {
                        session.close();
                    }
                } catch (IOException e) {
                    LOGGER.warn(e.getMessage(), e);
                }
            }
        };
    }

    static long durationMillis(RealTimeMessage.Payload payload) {
        if (payload == null) {
            return 0;
        }

        Integer beginTime = payload.getBeginTime();
        Integer time = payload.getTime();
        if (beginTime != null && time != null && time >= beginTime) {
            return time - beginTime;
        }

        // Without a sentence start, time is the only available task-level
        // duration approximation. If a start is present but the endpoint is
        // invalid, do not treat the absolute endpoint as a duration.
        if (beginTime == null && time != null && time >= 0) {
            return time;
        }

        return 0;
    }

    static class TranscriptionDurationAccumulator {
        private final AtomicLong absoluteDurationMillis = new AtomicLong();
        private final AtomicLong taskDurationFallbackMillis = new AtomicLong();
        private final AtomicBoolean absoluteTimingObserved = new AtomicBoolean();

        void record(RealTimeMessage.Payload payload) {
            if (payload != null && payload.getBeginTime() != null && payload.getTime() != null
                    && payload.getTime() >= payload.getBeginTime()) {
                absoluteTimingObserved.set(true);
                absoluteDurationMillis.addAndGet(durationMillis(payload));
            } else {
                // A text-only result may be emitted repeatedly as the task grows.
                // Keep the largest task duration and decide at close time whether
                // absolute sentence timing or the task-level fallback is usable.
                taskDurationFallbackMillis.accumulateAndGet(durationMillis(payload), Math::max);
            }
        }

        long seconds() {
            long durationMillis = absoluteTimingObserved.get()
                    ? absoluteDurationMillis.get()
                    : taskDurationFallbackMillis.get();
            return durationMillis <= 0 ? 0 : (durationMillis + 999) / 1000;
        }
    }
}
