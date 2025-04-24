package com.ke.bella.openapi.protocol.asr;

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
import okhttp3.WebSocketListener;
import okio.ByteString;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Slf4j
public class HuoshanStreamLMAsrCallback extends WebSocketListener implements Callbacks.WebSocketCallback {

    // 协议常量
    private static final byte PROTOCOL_VERSION = 0b0001;
    private static final byte DEFAULT_HEADER_SIZE = 0b0001;
    // Message Type:
    private static final byte FULL_CLIENT_REQUEST = 0b0001;
    private static final byte AUDIO_ONLY_REQUEST = 0b0010;
    private static final byte FULL_SERVER_RESPONSE = 0b1001;
    private static final byte SERVER_ACK = 0b1011;
    private static final byte SERVER_ERROR_RESPONSE = 0b1111;
    // Message Type Specific Flags
    private static final byte NO_SEQUENCE = 0b0000;// no check sequence
    private static final byte POS_SEQUENCE = 0b0001;
    private static final byte NEG_SEQUENCE = 0b0010;
    private static final byte NEG_WITH_SEQUENCE = 0b0011;
    private static final byte NEG_SEQUENCE_1 = 0b0011;
    // Message Serialization
    private static final byte NO_SERIALIZATION = 0b0000;
    private static final byte JSON = 0b0001;
    // Message Compression
    private static final byte NO_COMPRESSION = 0b0000;
    private static final byte GZIP = 0b0001;

    private final HuoshanRealTimeAsrRequest request;
    private final Callbacks.Sender sender;
    private final EndpointProcessData processData;
    private final EndpointLogger logger;
    private final Function<HuoshanLMRealTimeAsrResponse, List<String>> converter;
    private final CompletableFuture<?> startFlag = new CompletableFuture<>();
    private boolean end = false;
    private boolean first = true;
    private boolean isRunning = false;
    private long startTime = DateTimeUtils.getCurrentMills();
    private int audioSequence = 1; // 初始化为1

    public HuoshanStreamLMAsrCallback(HuoshanRealTimeAsrRequest request, Callbacks.Sender sender, EndpointProcessData processData,
            EndpointLogger logger, Function<HuoshanLMRealTimeAsrResponse, List<String>> converter) {
        this.request = request;
        this.sender = sender;
        this.processData = processData;
        this.logger = logger;
        this.converter = converter;
        processData.setMetrics(new HashMap<>());
    }

    @Data
    // 客户端请求参数类
    private static class ClientRequest {
        private App app;
        private User user;
        private ModelRequest request;
        private Audio audio;
    }

    @Data
    private static class App {
        private String appid;
        private String cluster;
        private String token;
    }

    @Data
    private static class User {
        private String uid;
    }

    @Data
    private static class ModelRequest {
        private String reqid;
        private boolean show_utterances;
        private String result_type;
        private Integer sequence;
        private String model_name;
        private boolean enable_itn;
        private boolean enable_punc;
        private boolean enable_ddc;
        private Integer vad_segment_duration;
        private Integer end_window_size;
        private Integer force_to_speech_time;
    }

    @Data
    private static class Audio {
        private String format;
        private String codec;
        private Integer rate;
        private Integer bits;
        private Integer channel;
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        try {
            LOGGER.info("WebSocket连接已打开");
            sendFullClientRequest(webSocket);
        } catch (Exception e) {
            LOGGER.error("WebSocket打开时出错", e);
            onError(ChannelException.fromException(e));
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        LOGGER.info("收到文本消息: {}", text);
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        try {
            parseResponse(bytes.toByteArray(), webSocket);
        } catch (Exception e) {
            LOGGER.error("处理WebSocket消息时出错", e);
            onError(ChannelException.fromException(e));
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        complete();
        LOGGER.info("WebSocket正在关闭: code={}, reason={}", code, reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        LOGGER.info("WebSocket已关闭: code={}, reason={}", code, reason);
        complete();
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        LOGGER.error("WebSocket连接失败", t);
        onError(ChannelException.fromException(t));
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
            sender.close();
            processData.getMetrics().put("ttlt", DateTimeUtils.getCurrentMills() - startTime);
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
        if (!end) {
            end = true;
            sender.onError(exception);
        }
    }

    /**
     * 处理处理过程中的错误
     */
    private void onProcessError(ChannelException exception) {
        if (!end) {
            sender.onError(exception);
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
            LOGGER.warn("发送完整客户端请求时出错", e);
            onError(ChannelException.fromException(e));
        }
    }

    /**
     * 构造完整的客户端请求
     */
    private byte[] constructFullClientRequest() throws Exception {
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

        // 设置请求信息 - 大模型特有参数
        ModelRequest modelRequest = new ModelRequest();
        modelRequest.setReqid(processData.getRequestId());
        modelRequest.setShow_utterances(true);
        modelRequest.setResult_type(request.getResultType());
        modelRequest.setSequence(1);
        // 大模型特有参数
        modelRequest.setModel_name("bigmodel"); // 大模型名称
        modelRequest.setEnable_punc(true); // 启用标点
        modelRequest.setEnable_itn(false); // 是否启用ITN
        modelRequest.setEnable_ddc(false); // 是否启用顺滑
        clientRequest.setRequest(modelRequest);

        // 设置音频信息
        Audio audio = new Audio();
        audio.setFormat(request.getFormat());
        audio.setRate(request.getSampleRate());
        audio.setBits(16); //默认为16
        audio.setChannel(1); //默认单声道
        audio.setCodec("raw"); // 默认为raw(pcm)
        clientRequest.setAudio(audio);

        // 将参数转换为JSON
        byte[] jsonPayload = JacksonUtils.toByte(clientRequest);

        // GZIP压缩
        byte[] compressedPayload = gzipCompress(jsonPayload);

        // 构建header
        byte[] header = getHeader(FULL_CLIENT_REQUEST, POS_SEQUENCE, JSON, GZIP, (byte) 0);

        // 构建序列号
        byte[] seqBytes = intToBytes(audioSequence);

        // 构建payload长度字节
        byte[] payloadSizeBytes = intToBytes(compressedPayload.length);

        // 拼接header、序列号、payload长度和payload
        byte[] fullClientRequest = new byte[header.length + seqBytes.length + payloadSizeBytes.length + compressedPayload.length];
        int destPos = 0;
        System.arraycopy(header, 0, fullClientRequest, destPos, header.length);
        destPos += header.length;
        System.arraycopy(seqBytes, 0, fullClientRequest, destPos, seqBytes.length);
        destPos += seqBytes.length;
        System.arraycopy(payloadSizeBytes, 0, fullClientRequest, destPos, payloadSizeBytes.length);
        destPos += payloadSizeBytes.length;
        System.arraycopy(compressedPayload, 0, fullClientRequest, destPos, compressedPayload.length);
        
        return fullClientRequest;
    }

    /**
     * 发送音频数据
     */
    public void sendAudioData(WebSocket webSocket, byte[] audioData, boolean isLast) {
        try {
            audioSequence++;
            
            // 如果是最后一块，将序列号设为负值
            int seq = audioSequence;
            if (isLast) {
                seq = -seq;
            }
            
            // 构造音频数据负载
            byte messageTypeSpecificFlags = isLast ? NEG_WITH_SEQUENCE : POS_SEQUENCE;
            
            // 构建header
            byte[] header = getHeader(AUDIO_ONLY_REQUEST, messageTypeSpecificFlags, JSON, GZIP, (byte) 0);
            
            // 构建序列号
            byte[] sequenceBytes = intToBytes(seq);
            
            // 压缩音频数据
            byte[] compressedAudio = gzipCompress(audioData);
            
            // 构建payload长度字节
            byte[] payloadSizeBytes = intToBytes(compressedAudio.length);
            
            // 拼接header、序列号、payload长度和payload
            byte[] audioOnlyRequest = new byte[header.length + sequenceBytes.length + payloadSizeBytes.length + compressedAudio.length];
            int destPos = 0;
            System.arraycopy(header, 0, audioOnlyRequest, destPos, header.length);
            destPos += header.length;
            System.arraycopy(sequenceBytes, 0, audioOnlyRequest, destPos, sequenceBytes.length);
            destPos += sequenceBytes.length;
            System.arraycopy(payloadSizeBytes, 0, audioOnlyRequest, destPos, payloadSizeBytes.length);
            destPos += payloadSizeBytes.length;
            System.arraycopy(compressedAudio, 0, audioOnlyRequest, destPos, compressedAudio.length);
            
            webSocket.send(ByteString.of(audioOnlyRequest));
        } catch (Exception e) {
            onProcessError(ChannelException.fromException(e));
        }
    }

    /**
     * 分块发送音频数据
     */
    public void sendAudioDataInChunks(WebSocket webSocket, byte[] audioData, int chunkSize, int intervalMs) {
        TaskExecutor.submit(() -> {
            try {
                int offset = 0;
                while (offset < audioData.length) {
                    int remaining = audioData.length - offset;
                    int length = Math.min(chunkSize, remaining);
                    byte[] chunk = new byte[length];
                    System.arraycopy(audioData, offset, chunk, 0, length);
                    
                    boolean isLast = (offset + length >= audioData.length);
                    sendAudioData(webSocket, chunk, isLast);
                    
                    offset += length;
                    
                    if (!isLast && intervalMs > 0) {
                        Thread.sleep(intervalMs);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("分块发送音频数据时出错", e);
                onProcessError(ChannelException.fromException(e));
            }
        });
    }

    /**
     * 解析服务器响应
     */
    private void parseResponse(byte[] message, WebSocket webSocket) {
        // 解析头部
        int headerLen = 4; // 固定头部长度为4字节
        int messageType = (message[1] & 0xf0) >> 4;
        int messageTypeFlag = message[1] & 0x0f;
        int messageSerial = (message[2] & 0xf0) >> 4;
        int messageCompress = message[2] & 0x0f;

        // 解析序列号和负载
        byte[] sequenceBytes = new byte[4];
        System.arraycopy(message, headerLen, sequenceBytes, 0, 4);
        int sequence = bytesToInt(sequenceBytes);

        // 解析负载大小
        byte[] payloadSizeBytes = new byte[4];
        System.arraycopy(message, headerLen + 4, payloadSizeBytes, 0, 4);
        int payloadSize = bytesToInt(payloadSizeBytes);

        // 提取负载数据
        byte[] payload = new byte[payloadSize];
        System.arraycopy(message, headerLen + 8, payload, 0, payloadSize);

        // 解压缩负载
        if (messageCompress == GZIP) {
            payload = gzipDecompress(payload);
        }

        // 处理不同类型的消息
        if (messageType == FULL_SERVER_RESPONSE) {
            // 解析JSON响应
            HuoshanLMRealTimeAsrResponse response = JacksonUtils.deserialize(payload, HuoshanLMRealTimeAsrResponse.class);

            // 检查响应状态
            if (response == null) {
                LOGGER.error("大模型ASR响应错误: {}", new String(payload));
                handleTranscriptionFailed(503, new String(payload));
                return;
            }

            // 处理响应
            if (!isRunning) {
                // 首次响应，设置运行标志
                isRunning = true;

                // 非流式请求直接发送文件，流式请求由客户端发送文件
                if (!request.isAsync()) {
                    sendAudioDataInChunks(webSocket, request.getAudioData(), request.getChunkSize(), request.getIntervalMs());
                } else {
                    startFlag.complete(null);
                }
            }

            // 检查响应码
            if (response.getCode() != 0) {
                LOGGER.error("大模型ASR响应错误: code={}, message={}", response.getCode(), response.getMessage());
                handleTranscriptionFailed(getHttpCode(response.getCode()), response.getMessage());
                return;
            }

            // 根据序列号判断是中间响应还是最终响应
            boolean isFinal = sequence < 0;
            if (isFinal) {
                handleFinalResponse(response);
            } else {
                handleIntermediateResponse(response);
            }
        } else if (messageType == SERVER_ERROR_RESPONSE) {
            // 处理服务器错误响应
            String errorMsg = new String(payload);
            LOGGER.error("服务器错误: {}", errorMsg);
            handleTranscriptionFailed(500, errorMsg);
        }
    }

    /**
     * 处理中间响应
     */
    private void handleIntermediateResponse(HuoshanLMRealTimeAsrResponse response) {
        try {
            if (converter != null) {
                List<String> results = converter.apply(response);
                if (results != null && !results.isEmpty()) {
                    for (String result : results) {
                        sender.send(result);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("处理中间响应时出错", e);
        }
    }

    /**
     * 处理最终响应
     */
    private void handleFinalResponse(HuoshanLMRealTimeAsrResponse response) {
        try {
            if (converter != null) {
                List<String> results = converter.apply(response);
                if (results != null && !results.isEmpty()) {
                    for (String result : results) {
                        sender.send(result);
                    }
                }
            }
            complete();
        } catch (Exception e) {
            LOGGER.error("处理最终响应时出错", e);
            onError(ChannelException.fromException(e));
        }
    }

    /**
     * 处理转录失败事件
     */
    private void handleTranscriptionFailed(int code, String errorMsg) {
        onError(ChannelException.fromResponse(code, errorMsg));
    }

    /**
     * 获取HTTP状态码
     */
    private int getHttpCode(int code) {
        return code == 0 ? 200 : 500;
    }

    /**
     * 获取消息头部
     */
    private byte[] getHeader(byte messageType, byte messageTypeSpecificFlags, byte serialMethod, byte compressionType,
                           byte reservedData) {
        final byte[] header = new byte[4];
        header[0] = (PROTOCOL_VERSION << 4) | DEFAULT_HEADER_SIZE; // Protocol version|header size
        header[1] = (byte) ((messageType << 4) | messageTypeSpecificFlags); // message type | messageTypeSpecificFlags
        header[2] = (byte) ((serialMethod << 4) | compressionType);
        header[3] = reservedData;
        return header;
    }

    /**
     * 整数转字节数组
     */
    private byte[] intToBytes(int a) {
        return new byte[]{
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    /**
     * 字节数组转整数
     */
    private int bytesToInt(byte[] src) {
        if (src == null || (src.length != 4)) {
            throw new IllegalArgumentException("Invalid byte array for int conversion");
        }
        return ((src[0] & 0xFF) << 24)
                | ((src[1] & 0xff) << 16)
                | ((src[2] & 0xff) << 8)
                | ((src[3] & 0xff));
    }

    /**
     * GZIP压缩
     */
    private byte[] gzipCompress(byte[] src) {
        if (src == null || src.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = null;
        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(src);
        } catch (IOException e) {
            LOGGER.error("GZIP压缩失败", e);
        } finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException e) {
                    LOGGER.error("关闭GZIP输出流失败", e);
                }
            }
        }
        return out.toByteArray();
    }

    /**
     * GZIP解压缩
     */
    private byte[] gzipDecompress(byte[] src) {
        if (src == null || src.length == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(src);
        GZIPInputStream gzip = null;
        try {
            gzip = new GZIPInputStream(in);
            byte[] buffer = new byte[1024];
            int n;
            while ((n = gzip.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            LOGGER.error("GZIP解压缩失败", e);
        } finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException e) {
                    LOGGER.error("关闭GZIP输入流失败", e);
                }
            }
        }
        return out.toByteArray();
    }
}
