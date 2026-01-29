package com.ke.bella.openapi.protocol.realtime;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.Callbacks.WebSocketCallback;
import com.ke.bella.openapi.protocol.asr.AsrProperty;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.WebSocket;

/**
 * 实时语音WebSocket处理器
 */

public class RealTimeHandler extends TextWebSocketHandler {

    private final String url;
    private final AsrProperty property;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;
    private final RealTimeAdaptor<AsrProperty> adaptor;
    private static final Logger LOGGER = LoggerFactory.getLogger(RealTimeHandler.class);
    private String taskId;
    // 与ASR服务的WebSocket连接
    private WebSocket ws;
    private WebSocketCallback callback;

    public RealTimeHandler(String url, AsrProperty property, EndpointProcessData processData, EndpointLogger logger,
            RealTimeAdaptor<AsrProperty> adaptor) {
        this.url = url;
        this.property = property;
        this.processData = processData;
        this.logger = logger;
        this.adaptor = adaptor;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LOGGER.info("客户端WebSocket连接已建立: {}", session.getId());
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
                return;
            }

            RealTimeEventType eventType = RealTimeEventType.fromString(realTimeMessage.getHeader().getName());
            switch (eventType) {
            case START_TRANSCRIPTION:
                realTimeMessage.setApikey(processData.getApikey());
                handleStartTranscription(session, realTimeMessage);
                break;
            case STOP_TRANSCRIPTION:
                handleStopTranscription(session, realTimeMessage);
                break;
            default:
                LOGGER.warn("不支持的事件类型: " + realTimeMessage.getHeader().getName());
                sendErrorResponse(session, 40000000, "不支持的事件类型: " + realTimeMessage.getHeader().getName());
                break;
            }
        } catch (Exception e) {
            LOGGER.warn("处理文本消息时出错: {}", e.getMessage());
            LOGGER.warn(e.getMessage(), e);
            sendErrorResponse(session, 50000000, "处理请求时出错: " + e.getMessage());
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
            sendErrorResponse(session, 50000000, "处理音频数据时出错: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LOGGER.info("客户端WebSocket连接已关闭, status: {}", status);

        // 关闭与第三方ws服务的连接
        if(ws != null) {
            adaptor.closeConnection(ws);
            ws = null;
        }

        taskId = null;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOGGER.warn("WebSocket传输错误: {}", exception.getMessage());

        // 关闭与第三方服务的连接
        if(ws != null) {
            adaptor.closeConnection(ws);
            ws = null;
        }

        taskId = null;
    }

    private void handleStartTranscription(WebSocketSession session, RealTimeMessage request) throws IOException {
        if(taskId != null) {
            sendErrorResponse(session, 40000000, "已有转录任务正在进行");
            return;
        }

        // 获取或生成任务ID
        taskId = request.getHeader().getTaskId();
        if(taskId == null) {
            taskId = UUID.randomUUID().toString();
        }

        // 创建回调处理器
        callback = adaptor.createCallback(webSocketSender(session, processData), processData, logger, taskId, request, property);

        // 创建与第三方服务的连接并开始转录
        ws = adaptor.startTranscription(url, property, request, callback);

        if(ws == null) {
            taskId = null;
            sendErrorResponse(session, 50000000, "无法连接到ASR服务");
            return;
        }

        // 发送TranscriptionStarted响应
        sendTranscriptionStartedResponse(session, taskId);
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
            sendErrorResponse(session, 50000000, "无法停止转录任务");
        }
    }

    private void sendTranscriptionStartedResponse(WebSocketSession session, String taskId) throws IOException {
        RealTimeMessage response = RealTimeMessage.startedResponse(taskId);
        session.sendMessage(new TextMessage(JacksonUtils.serialize(response)));
    }

    private RealTimeMessage sendErrorResponse(WebSocketSession session, int status, String errorMessage) {
        if(!session.isOpen()) {
            return null;
        }
        int httpCode = status >= 50000000 ? 500 : 400;
        RealTimeMessage response = RealTimeMessage.errorResponse(httpCode, status, errorMessage, taskId);
        try {
            session.sendMessage(new TextMessage(JacksonUtils.serialize(response)));
        } catch (IOException e) {
            LOGGER.warn("发送错误响应失败: {}", e.getMessage());
        }
        return response;
    }

    private Callbacks.Sender webSocketSender(WebSocketSession session, EndpointProcessData processData) {
        final AtomicInteger duration = new AtomicInteger(0);
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
                }
                RealTimeMessage message = JacksonUtils.deserialize(text, RealTimeMessage.class);
                if(message.getHeader().getName().equals(RealTimeEventType.SENTENCE_END.getValue())) {
                    int time = (int) Math.ceil((message.getPayload().getTime() - message.getPayload().getBeginTime()) / 1000.0);
                    duration.getAndAdd(time);
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
                }
            }

            @Override
            public void onError(Throwable e) {
                BellaException exception = BellaException.fromException(e);
                RealTimeMessage res = sendErrorResponse(session, exception.getHttpCode() < 500 ? 40000000 : 50000000, exception.getMessage());
                processData.setResponse(res);
            }

            @Override
            public void close() {
                try {
                    processData.setTranscriptionDuration(duration.get());
                    session.close();
                } catch (IOException e) {
                    LOGGER.warn(e.getMessage(), e);
                }
            }
        };
    }
}
