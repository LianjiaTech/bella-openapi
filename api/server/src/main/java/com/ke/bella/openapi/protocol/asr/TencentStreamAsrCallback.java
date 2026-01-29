package com.ke.bella.openapi.protocol.asr;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * 腾讯实时语音识别回调处理器
 */
@Slf4j
public class TencentStreamAsrCallback implements Callbacks.WebSocketCallback {

    private final TencentRealTimeAsrRequest request;
    private final Callbacks.Sender sender;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;
    private final Function<TencentRealTimeAsrResponse, List<String>> converter;

    private final CompletableFuture<?> startFlag = new CompletableFuture<>();

    // 状态标志
    private boolean end = false;
    private boolean first = true;
    private boolean isRunning = false;

    // 性能指标
    private long startTime = DateTimeUtils.getCurrentMills();

    public TencentStreamAsrCallback(
            TencentRealTimeAsrRequest request,
            Callbacks.Sender sender,
            EndpointProcessData processData,
            EndpointLogger logger,
            Function<TencentRealTimeAsrResponse, List<String>> converter) {
        this.request = request;
        this.sender = sender;
        this.processData = processData;
        this.logger = logger;
        this.converter = converter;
        processData.setMetrics(new HashMap<>());
    }

    /**
     * 获取请求对象
     */
    public TencentRealTimeAsrRequest getRequest() {
        return request;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        try {
            log.info("Tencent ASR WebSocket connection established, voice_id: {}", request.getVoiceId());
            processData.setChannelRequestId(request.getVoiceId());

            // WebSocket连接建立后，腾讯会先返回握手成功消息
            // 不需要主动发送初始化消息，等待服务端的握手响应
            isRunning = true;
        } catch (Exception e) {
            log.error("Tencent ASR onOpen error", e);
            onError(ChannelException.fromException(e));
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        try {
            log.debug("Received text message: {}", text);
            TencentRealTimeAsrResponse response = JacksonUtils.deserialize(text, TencentRealTimeAsrResponse.class);
            handleResponse(response, webSocket);
        } catch (Exception e) {
            log.error("Error parsing text message", e);
            onProcessError(ChannelException.fromException(e));
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        // 腾讯ASR使用文本消息，不使用二进制消息
        log.warn("Received unexpected binary message");
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        log.info("Tencent ASR WebSocket closing: code={}, reason={}", code, reason);
        complete();
        webSocket.close(1000, "Client closing");
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        log.info("Tencent ASR WebSocket closed: code={}, reason={}", code, reason);
        complete();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        log.error("Tencent ASR WebSocket failure: {}", t.getMessage(), t);

        int httpCode = response != null ? response.code() : 500;
        String message = t.getMessage();

        onError(new ChannelException.OpenAIException(httpCode, message));
    }

    @Override
    public boolean started() {
        try {
            startFlag.get(30, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ChannelException.fromException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw ChannelException.fromException(e);
        }
    }

    /**
     * 发送音频数据
     */
    public void sendAudioData(WebSocket webSocket, byte[] audioData) {
        if(audioData != null && audioData.length > 0) {
            webSocket.send(ByteString.of(audioData));
        }
    }

    /**
     * 发送结束标识
     */
    public void sendEnd(WebSocket webSocket) {
        String endMessage = "{\"type\": \"end\"}";
        webSocket.send(endMessage);
        log.info("Sent end message to Tencent ASR");
    }

    /**
     * 处理服务端响应
     */
    private void handleResponse(TencentRealTimeAsrResponse response, WebSocket webSocket) {
        // 检查响应状态码
        if(!response.isSuccess()) {
            log.error("Tencent ASR error response: code={}, message={}", response.getCode(), response.getMessage());
            handleTranscriptionFailed(response.getCode(), response.getMessage());
            return;
        }

        // 握手成功响应
        if(response.getResult() == null && response.getCode() == 0) {
            log.info("Tencent ASR handshake success: {}", response.getMessage());

            // 流式请求等待客户端发送数据
            startFlag.complete(null);
            return;
        }

        // 处理识别结果
        if(response.getResult() != null) {
            handleTranscriptionResult(response);
        }

        // 处理最终消息
        if(response.isFinal()) {
            log.info("Received final message, ending transcription");
            complete();
        }
    }

    /**
     * 处理识别结果
     */
    private void handleTranscriptionResult(TencentRealTimeAsrResponse response) {
        if(first) {
            processData.getMetrics().put("ttft", DateTimeUtils.getCurrentMills() - startTime);
            first = false;
        }

        // 使用转换器将响应转换为标准格式
        List<String> texts = converter.apply(response);
        for (String text : texts) {
            sender.send(text);
        }
    }

    /**
     * 处理转录失败
     */
    private void handleTranscriptionFailed(int code, String errorMsg) {
        log.error("Transcription failed: code={}, message={}", code, errorMsg);
        isRunning = false;
        sender.onError(new ChannelException.OpenAIException(getHttpCode(code), errorMsg));

        complete();
    }

    /**
     * 将腾讯错误码映射为HTTP状态码
     */
    private int getHttpCode(int code) {
        // 根据腾讯ASR错误码文档映射
        if(code == 0) {
            return 200;
        }
        // 认证相关错误
        if(code >= 4000 && code <= 4003) {
            return 401;
        }
        // 限流错误
        if(code == 4005 || code == 4006) {
            return 429;
        }
        // 服务端错误
        if(code >= 4008 && code <= 4012) {
            return 503;
        }
        // 其他错误
        return 400;
    }

    /**
     * 完成处理并关闭连接
     */
    private void complete() {
        if(!end) {
            processData.getMetrics().put("ttlt", DateTimeUtils.getCurrentMills() - startTime);
            sender.close();
            if(logger != null) {
                logger.log(processData);
            }
            end = true;
        }
    }

    /**
     * 处理错误
     */
    private void onError(ChannelException exception) {
        log.warn("Tencent ASR error: {}", exception.getMessage(), exception);
        sender.onError(exception);
        complete();
    }

    /**
     * 处理处理过程中的错误
     */
    private void onProcessError(ChannelException exception) {
        log.warn("Tencent ASR process error: {}", exception.getMessage(), exception);
        sender.onError(exception);
        complete();

    }
}
