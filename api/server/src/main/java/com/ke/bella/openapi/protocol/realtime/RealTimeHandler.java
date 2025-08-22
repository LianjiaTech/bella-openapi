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
import com.ke.bella.openapi.common.exception.ChannelException;
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
    // 音频发送失败计数器
    private int audioSendFailureCount = 0;
    private static final int MAX_AUDIO_SEND_FAILURES = 3;

    public RealTimeHandler(String url, AsrProperty property, EndpointProcessData processData, EndpointLogger logger, RealTimeAdaptor<AsrProperty> adaptor) {
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
            if (realTimeMessage == null || realTimeMessage.getHeader() == null || realTimeMessage.getHeader().getName() == null) {
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
            sendErrorResponse(session, 50000000,"处理请求时出错: " + e.getMessage());
        }
    }
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            byte[] audioData = message.getPayload().array();
            
            if (taskId == null) {
                sendErrorResponse(session, 40000000,"未开始转录任务，请先发送StartTranscription指令");
                return;
            }
            
            if (ws == null) {
                sendErrorResponse(session, 50000000,"未连接到ASR服务");
                return;
            }
            
            // 发送音频数据到第三方服务
            boolean success = adaptor.sendAudioData(ws, audioData, callback);
            if (!success) {
                audioSendFailureCount++;
                LOGGER.warn("音频数据发送失败，失败次数: {}/{}", audioSendFailureCount, MAX_AUDIO_SEND_FAILURES);
                
                sendErrorResponse(session, 50000000,"发送音频数据失败");
                
                // 如果连续失败次数达到阈值，主动断开连接
                if (audioSendFailureCount >= MAX_AUDIO_SEND_FAILURES) {
                    LOGGER.warn("音频数据连续发送失败达到上限({})，主动断开WebSocket连接", MAX_AUDIO_SEND_FAILURES);
                    closeConnectionAndCleanup(session);
                    return;
                }
            } else {
                // 发送成功时重置失败计数器
                if (audioSendFailureCount > 0) {
                    LOGGER.info("音频数据发送恢复成功，重置失败计数器");
                    audioSendFailureCount = 0;
                }
            }
            
        } catch (Exception e) {
            LOGGER.warn("处理二进制消息时出错: {}", e.getMessage());
            sendErrorResponse(session, 50000000,"处理音频数据时出错: " + e.getMessage());
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LOGGER.info("客户端WebSocket连接已关闭, status: {}", status);
        cleanupResources();
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOGGER.warn("WebSocket传输错误: {}", exception.getMessage());
        cleanupResources();
    }

    private void handleStartTranscription(WebSocketSession session, RealTimeMessage request) throws IOException {
        if (taskId != null) {
            sendErrorResponse(session, 40000000,"已有转录任务正在进行");
            return;
        }
        
        // 获取或生成任务ID
        taskId = request.getHeader().getTaskId();
        if (taskId == null) {
            taskId = UUID.randomUUID().toString();
        }
        
        // 重置失败计数器
        audioSendFailureCount = 0;
        
        // 创建回调处理器
        callback = adaptor.createCallback(webSocketSender(session, processData), processData, logger, taskId, request, property);
        
        // 创建与第三方服务的连接并开始转录
        ws = adaptor.startTranscription(url, property, request, callback);
        
        if (ws == null) {
            taskId = null;
            sendErrorResponse(session, 50000000,"无法连接到ASR服务");
            return;
        }

        // 发送TranscriptionStarted响应
        sendTranscriptionStartedResponse(session, taskId);
    }
    
    private void handleStopTranscription(WebSocketSession session, RealTimeMessage request) {
        if (taskId == null || ws == null) {
            sendErrorResponse(session, 40000000, "没有正在进行的转录任务");
            return;
        }
        
        String msgTaskId = request.getHeader().getTaskId();
        if (msgTaskId != null && !msgTaskId.equals(taskId)) {
            sendErrorResponse(session, 40000000, "无效的任务ID");
            return;
        }
        
        // 发送结束转录指令
        boolean success = adaptor.stopTranscription(ws, request, callback);
        
        if (!success) {
            sendErrorResponse(session, 50000000,"无法停止转录任务");
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
        int httpCode =  status >= 50000000 ? 500 : 400;
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
                ChannelException exception = ChannelException.fromException(e);
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

    /**
     * 关闭连接并清理资源
     */
    private void closeConnectionAndCleanup(WebSocketSession session) {
        try {
            // 发送TaskFailed消息通知客户端连接即将关闭
            RealTimeMessage taskFailedMessage = RealTimeMessage.taskFailedResponse(
                taskId, 50000000, "音频数据连续发送失败，连接已断开"
            );
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(JacksonUtils.serialize(taskFailedMessage)));
            }
            
            // 主动关闭客户端连接
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR.withReason("音频数据发送失败次数过多"));
            }
        } catch (IOException e) {
            LOGGER.warn("关闭WebSocket连接时出错: {}", e.getMessage());
        } finally {
            cleanupResources();
        }
    }

    /**
     * 清理资源
     */
    private void cleanupResources() {
        // 关闭与第三方ws服务的连接
        if (ws != null) {
            try {
                adaptor.closeConnection(ws);
            } catch (Exception e) {
                LOGGER.warn("关闭第三方WebSocket连接时出错: {}", e.getMessage());
            }
            ws = null;
        }
        
        taskId = null;
        audioSendFailureCount = 0;
        callback = null;
    }
}
