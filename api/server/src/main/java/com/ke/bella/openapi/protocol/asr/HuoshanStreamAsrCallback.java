package com.ke.bella.openapi.protocol.asr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.WebSocket;
import okio.ByteString;


@Slf4j
public class HuoshanStreamAsrCallback implements Callbacks.WebSocketCallback {
    // 协议版本常量
    private static final class ProtocolVersion {
        static final int PROTOCOL_VERSION = 0b0001;
    }

    // 消息类型常量
    private static final class MessageType {
        static final int FULL_CLIENT_REQUEST = 0b0001;
        static final int AUDIO_ONLY_CLIENT_REQUEST = 0b0010;
        static final int FULL_SERVER_RESPONSE = 0b1001;
        static final int SERVER_ACK = 0b1011;
        static final int ERROR_MESSAGE_FROM_SERVER = 0b1111;
    }

    // 消息类型标志常量
    private static final class MessageTypeFlag {
        static final int NO_SEQUENCE_NUMBER = 0b0000;
        static final int POSITIVE_SEQUENCE_CLIENT_ASSGIN = 0b0001;
        static final int NEGATIVE_SEQUENCE_SERVER_ASSGIN = 0b0010;
        static final int NEGATIVE_SEQUENCE_CLIENT_ASSGIN = 0b0011;
    }

    // 消息序列化方式常量
    private static final class MessageSerial {
        static final int NO_SERIAL = 0b0000;
        static final int JSON = 0b0001;
        static final int CUSTOM_SERIAL = 0b1111;
    }

    // 消息压缩方式常量
    private static final class MessageCompress {
        static final int NO_COMPRESS = 0b0000;
        static final int GZIP = 0b0001;
        static final int CUSTOM_COMPRESS = 0b1111;
    }

    private final HuoshanRealTimeAsrRequest request;
    private final Sender sender;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;
    private final Function<HuoshanRealTimeAsrResponse, List<String>> converter;

    private final CompletableFuture<?> startFlag = new CompletableFuture<>();

    // 状态标志
    private boolean end = false;
    private boolean first = true;
    private boolean isRunning = false;
    
    // 性能指标
    private long startTime = DateTimeUtils.getCurrentMills();

    // 序列号管理
    private int audioSequence = 0;


    public HuoshanStreamAsrCallback(HuoshanRealTimeAsrRequest request, Callbacks.Sender sender, EndpointProcessData processData,
            EndpointLogger logger, Function<HuoshanRealTimeAsrResponse, List<String>> converter) {
        this.request = request;
        this.sender = sender;
        this.processData = processData;
        this.logger = logger;
        this.converter = converter;
        processData.setMetrics(new HashMap<>());
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        try {
            
            LOGGER.info("ASR WebSocket connection established, logId: {}", response.header("X-Tt-Logid"));
            processData.setChannelRequestId(response.header("X-Tt-Logid"));

            // 发送完整的客户端请求
            sendFullClientRequest(webSocket);
        } catch (Exception e) {
            LOGGER.error("ASR onOpen error", e);
            onError(ChannelException.fromException(e));
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {

    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        try {
            parseResponse(bytes.toByteArray(), webSocket);
        } catch (Exception e) {
            onProcessError(ChannelException.fromException(e));
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        complete();
        webSocket.close(1000, "Client closing");
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        LOGGER.info("ASR onClosed: code={}, reason={}", code, reason);
        complete();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        LOGGER.error("ASR onFailure: {}", t.getMessage(), t);
        
        int httpCode = response != null ? response.code() : 500;
        String message = t.getMessage();
        
        onError(ChannelException.fromResponse(httpCode, message));
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
     * 完成处理并关闭连接
     */
    private void complete() {
        if (!end) {
            processData.getMetrics().put("ttlt", DateTimeUtils.getCurrentMills() - startTime);
            sender.close();
            if(request.isAsync() && logger != null) {
                logger.log(processData);
            }
            end = true;
        }
    }

    /**
     * 处理错误
     */
    private void onError(ChannelException exception) {
        LOGGER.warn("ASR error: {}", exception.getMessage(), exception);
        sender.onError(exception);
        complete();
    }

    private void onProcessError(ChannelException exception) {
        LOGGER.warn("ASR error: {}", exception.getMessage(), exception);
        sender.onError(exception);
        if(!request.isAsync()) {
            complete();
        }
    }

    /**
     * 发送完整的客户端请求
     */
    private void sendFullClientRequest(WebSocket webSocket) {
        try {
            byte[] payload = constructFullClientRequest();
            webSocket.send(ByteString.of(payload));
        } catch (Exception e) {
            LOGGER.warn("Error sending full client request", e);
            onError(ChannelException.fromException(e));
        }
    }

    /**
     * 构造完整的客户端请求
     */
    private byte[] constructFullClientRequest() throws Exception {
        int headerLen = 4;
        byte[] header = new byte[headerLen];
        header[0] = (byte) (ProtocolVersion.PROTOCOL_VERSION << 4 | (headerLen >> 2));
        header[1] = (byte) (MessageType.FULL_CLIENT_REQUEST << 4 | MessageTypeFlag.NO_SEQUENCE_NUMBER);
        header[2] = (byte) (MessageSerial.JSON << 4 | MessageCompress.GZIP);
        header[3] = 0;

        // 构建请求参数
        ClientRequest clientRequest = new ClientRequest();
        
        // 设置App信息
        App app = new App();
        app.setAppid(request.getAppId());
        app.setCluster(request.getCluster());
        app.setToken(request.getToken());
        clientRequest.setApp(app);
        
        // 设置用户信息
        User user = new User();
        user.setUid(request.getUid());
        clientRequest.setUser(user);
        
        // 设置请求信息
        ModelRequest modelRequest = new ModelRequest();
        modelRequest.setReqid(processData.getRequestId());
        modelRequest.setShow_utterances(true);
        modelRequest.setResult_type(request.getResultType());
        modelRequest.setSequence(1);
        clientRequest.setRequest(modelRequest);
        
        // 设置音频信息
        Audio audio = new Audio();
        audio.setFormat(request.getFormat());
        audio.setRate(request.getSampleRate());
        clientRequest.setAudio(audio);

        // 将参数转换为JSON
        byte[] jsonPayload = JacksonUtils.toByte(clientRequest);

        // GZIP压缩
        byte[] compressedPayload = gzipCompress(jsonPayload);

        // 构建payload长度字节
        int payloadLen = compressedPayload.length;
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(payloadLen);
        byte[] payloadLenBytes = bb.array();

        // 拼接header、payload长度和payload
        return concatBytes(header, payloadLenBytes, compressedPayload);
    }

    /**
     * 发送音频数据
     */
    public void sendAudioData(WebSocket webSocket, byte[] audioData, boolean isLast) {
        try {
            // 增加序列号
            audioSequence++;
            
            byte[] payload = constructAudioPayload(audioData, isLast);
            webSocket.send(ByteString.of(payload));
        } catch (Exception e) {
            onProcessError(ChannelException.fromException(e));
        }
    }

    /**
     * 构造音频数据负载
     */
    private byte[] constructAudioPayload(byte[] audio, boolean isLast) throws IOException {
        int headerLen = 4;
        byte[] header = new byte[headerLen];
        header[0] = (byte) (ProtocolVersion.PROTOCOL_VERSION << 4 | (headerLen >> 2));
    
        if (!isLast) {
            // 非最后一块，使用无序列号
            header[1] = (byte) (MessageType.AUDIO_ONLY_CLIENT_REQUEST << 4 | MessageTypeFlag.NO_SEQUENCE_NUMBER);
        } else {
            // 最后一块，使用负序列号
            header[1] = (byte) (MessageType.AUDIO_ONLY_CLIENT_REQUEST << 4 | MessageTypeFlag.NEGATIVE_SEQUENCE_SERVER_ASSGIN);
        }
    
        // 使用JSON序列化方式，与HuoshanFlashDemo保持一致
        header[2] = (byte) (MessageSerial.JSON << 4 | MessageCompress.GZIP);
        header[3] = 0;
    
        // GZIP压缩
        byte[] compressedPayload = gzipCompress(audio);
    
        // 构建payload长度字节
        int payloadLen = compressedPayload.length;
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(payloadLen);
        byte[] payloadLenBytes = bb.array();
    
        // 拼接header、payload长度和payload
        return concatBytes(header, payloadLenBytes, compressedPayload);
    }

    /**
     * 分块发送音频数据
     */
    public void sendAudioDataInChunks(WebSocket webSocket, byte[] audioData, int chunkSize, int intervalMs) {
        if (audioData == null || audioData.length == 0) {
            onProcessError(ChannelException.fromResponse(400, "No audio data to send"));
            return;
        }
        
        TaskExecutor.submit(() -> {
            try {
                int offset = 0;
                while (offset < audioData.length && isRunning) {
                    int length = Math.min(chunkSize, audioData.length - offset);
                    byte[] chunk = new byte[length];
                    System.arraycopy(audioData, offset, chunk, 0, length);
                    
                    boolean isLast = (offset + length >= audioData.length);
                    sendAudioData(webSocket, chunk, isLast);
                    offset += length;
                    // 如果不是最后一个块，等待指定的间隔时间
                    if (!isLast && intervalMs > 0) {
                        Thread.sleep(intervalMs);
                    }
                }
            } catch (Exception e) {
                onProcessError(ChannelException.fromException(e));
            }
        });
    }

    /**
     * 解析服务器响应
     */
    private void parseResponse(byte[] message, WebSocket webSocket) {
        int headerLen = (message[0] & 0x0f) << 2;
        int messageType = (message[1] & 0xf0) >> 4;
        int messageSerial = (message[2] & 0xf0) >> 4;
        int messageCompress = message[2] & 0x0f;

        byte[] payload;
        int payloadOffset = headerLen;

        if (messageType == MessageType.FULL_SERVER_RESPONSE) {
            payloadOffset += 4;
        } else if (messageType == MessageType.SERVER_ACK) {
            payloadOffset += 4;
            if (message.length > 8) {
                payloadOffset += 4;
            }
        } else if (messageType == MessageType.ERROR_MESSAGE_FROM_SERVER) {
            payloadOffset += 4;
            payloadOffset += 4;
        } else {
            onProcessError(ChannelException.fromResponse(400, "Unsupported message type"));
            return;
        }

        payload = new byte[message.length - payloadOffset];
        System.arraycopy(message, payloadOffset, payload, 0, payload.length);

        if (messageCompress == MessageCompress.GZIP) {
            payload = gzipDecompress(payload);
        }

        if (messageSerial == MessageSerial.JSON) {
            String responseText = new String(payload, java.nio.charset.StandardCharsets.UTF_8);


            HuoshanRealTimeAsrResponse response = JacksonUtils.deserialize(payload, HuoshanRealTimeAsrResponse.class);

            // 处理响应
            if (response.getCode() != 1000) {
                LOGGER.error("ASR response error: {}", responseText);
                handleTranscriptionFailed(response.getCode(), response.getMessage());
                return;
            }

            if(!isRunning) {
                // 设置运行标志
                isRunning = true;
                //非流式请求直接发送文件，流式请求由客户端发送文件
                if(!request.isAsync()) {
                    sendAudioDataInChunks(webSocket, request.getAudioData(), request.getChunkSize(), request.getIntervalMs());
                } else {
                    startFlag.complete(null);
                }
            } else {
                // 处理事件
                if(response.getSequence() < 0) {
                    // 负序列号表示最终响应
                    handleFinalResponse(response);
                } else if(request.isAsync()) {
                    // 处理中间结果
                    handleIntermediateResponse(response);
                }
            }
        }
    }

    /**
     * 处理中间响应
     */
    private void handleIntermediateResponse(HuoshanRealTimeAsrResponse response) {
        if (first) {
            processData.getMetrics().put("ttft", DateTimeUtils.getCurrentMills() - startTime);
            first = false;
        }
        List<String> texts = converter.apply(response);
        for (String text : texts) {
            sender.send(text);
        }
    }

    /**
     * 处理最终响应
     */
    private void handleFinalResponse(HuoshanRealTimeAsrResponse response) {
        List<String> texts = converter.apply(response);
        for (String text : texts) {
            sender.send(text);
        }
        // 完成转录
        isRunning = false;
        complete();
    }

    /**
     * 处理转录失败事件
     */
    private void handleTranscriptionFailed(int code, String errorMsg) {
        LOGGER.error("Transcription failed: {}", errorMsg);
        isRunning = false;
        sender.onError(ChannelException.fromResponse(getHttpCode(code), errorMsg));
        if(!request.isAsync()) {
            complete();
        }
    }

    private int getHttpCode(int code) {
        if(code == 1000) {
            return 200;
        }
        if(code == 1002) {
            return 401;
        }
        if(code == 1003 || code == 1004) {
            return 429;
        }
        if(code > 1019) {
            return 503;
        }
        return 400;
    }

    /**
     * GZIP压缩
     */
    private byte[] gzipCompress(byte[] data) throws IOException {
        if(data == null) {
            data = new byte[0];
        }
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(data.length);
        try (GZIPOutputStream gzipOut = new GZIPOutputStream(byteStream)) {
            gzipOut.write(data);
        }
        return byteStream.toByteArray();
    }

    /**
     * GZIP解压缩
     */
    private byte[] gzipDecompress(byte[] data) {
        try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPInputStream gzipIn = new GZIPInputStream(byteStream)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = gzipIn.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
            return out.toByteArray();
        } catch (Exception e) {
            LOGGER.warn(e.getMessage(), e);
            return new byte[0];
        }
    }

    /**
     * 拼接字节数组
     */
    private byte[] concatBytes(byte[] first, byte[] second, byte[] third) {
        byte[] result = new byte[first.length + second.length + third.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        System.arraycopy(third, 0, result, first.length + second.length, third.length);
        return result;
    }

    /**
     * 请求实体类
     */
    @Data
    public static class ClientRequest {
        private App app;
        private User user;
        private ModelRequest request;
        private Audio audio;
    }

    @Data
    public static class App {
        private String appid;
        private String cluster;
        private String token;
    }

    @Data
    public static class User {
        private String uid;
    }

    @Data
    public static class ModelRequest {
        private String reqid;
        private boolean show_utterances = true;
        private String result_type;
        private Integer sequence;
    }

    @Data
    public static class Audio {
        private String format;
        private String codec;
        private Integer rate;
        private Integer bits;
        private Integer channels;
    }
}
