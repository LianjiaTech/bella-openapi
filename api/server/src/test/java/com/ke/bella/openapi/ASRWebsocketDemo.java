package com.ke.bella.openapi;

import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;
import okio.ByteString;
import org.apache.log4j.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 贝壳实时ASR WebSocket Demo
 */
public class ASRWebsocketDemo {
    private static final Logger logger = Logger.getLogger(ASRWebsocketDemo.class);

    // 替换为您的API Token
    private static final String TOKEN = "Bearer *************";

    // 替换为您的modelName
    private static final String MODEL = "";
    
    // WebSocket服务器地址
    private static final String WEBSOCKET_URL = "ws://localhost:8080/v1/audio/asr/stream?model=" + MODEL;

    // 替换为您的音频文件路径
    private static final String AUDIO_FILE_PATH = "test.wav";
    
    // 用于同步的锁
    private static final CountDownLatch latch = new CountDownLatch(1);
    
    // 是否正在运行
    private static boolean isRunning = false;

    public static void main(String[] args) throws Exception {
        for(int i = 0; i < 1; i++) {
            Thread thread = new Thread(() -> {

                // 解析音频元数据
                AudioFormat format;
                AudioInputStream audioInputStream;
                try {
                    File audioFile = new File(AUDIO_FILE_PATH);
                    audioInputStream = AudioSystem.getAudioInputStream(audioFile);
                    format = audioInputStream.getFormat();
                } catch (UnsupportedAudioFileException | IOException e) {
                    throw new RuntimeException("无法读取音频文件: " + e.getMessage(), e);
                }

                // 设置WebSocket请求
                Request request = new Request.Builder()
                        .url(WEBSOCKET_URL)
                        .header("Authorization", TOKEN)
                        .build();

                // 配置OkHttp客户端
                HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
                loggingInterceptor.setLevel(Level.HEADERS);
                OkHttpClient client = new OkHttpClient.Builder()
                        .pingInterval(30, TimeUnit.SECONDS)
                        .addInterceptor(loggingInterceptor)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .writeTimeout(60, TimeUnit.SECONDS)
                        .build();

                // 生成任务ID
                String taskId = UUID.randomUUID().toString();

                // 建立WebSocket连接
                client.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        logger.info("WebSocket连接已建立");

                        // 发送StartTranscription指令
                        String startTranscriptionJson = generateStartTranscriptionJson(taskId, format);
                        webSocket.send(startTranscriptionJson);
                        logger.info("已发送StartTranscription指令: " + startTranscriptionJson);
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        logger.info("收到文本消息: " + text);

                        try {
                            Map<String, Object> message = JacksonUtils.deserialize(text, Map.class);
                            Map<String, Object> header = (Map<String, Object>) message.get("header");
                            Map<String, Object> payload = (Map<String, Object>) message.get("payload");

                            if(header != null && header.containsKey("name")) {
                                String eventName = (String) header.get("name");

                                switch (eventName) {
                                case "TranscriptionStarted":
                                    logger.info("转录已开始");
                                    isRunning = true;

                                    // 开始发送音频数据
                                    new Thread(() -> {
                                        try {
                                            sendAudioStream(webSocket, AUDIO_FILE_PATH);

                                            // 发送StopTranscription指令结束任务
                                            String stopTranscriptionJson = generateStopTranscriptionJson(taskId);
                                            webSocket.send(stopTranscriptionJson);
                                            logger.info("已发送StopTranscription指令: " + stopTranscriptionJson);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }).start();
                                    break;

                                case "SentenceStart":
                                    logger.info("开始识别新的一句话");
                                    break;

                                case "TranscriptionResultChanged":
                                    if(payload != null && payload.containsKey("result")) {
                                        String transcript = (String) payload.get("result");
                                        logger.info("部分识别结果: " + transcript);
                                    }
                                    break;

                                case "SentenceEnd":
                                    logger.info("一句话识别结束");
                                    if(payload != null && payload.containsKey("result")) {
                                        String transcript = (String) payload.get("result");
                                        logger.info("最终识别结果: " + transcript);
                                    }
                                    break;

                                case "TranscriptionCompleted":
                                    logger.info("转录已完成");
                                    isRunning = false;
                                    webSocket.close(1000, "正常关闭");
                                    latch.countDown();
                                    break;

                                case "TranscriptionFailed":
                                    String errorMessage = header.containsKey("status_message") ?
                                            (String) header.get("status_message") : "未知错误";
                                    logger.info("转录失败: " + errorMessage);
                                    isRunning = false;
                                    webSocket.close(1000, "转录失败");
                                    latch.countDown();
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            logger.info("解析消息失败: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, ByteString bytes) {
                        logger.info("收到二进制消息，长度: " + bytes.size());
                    }

                    @Override
                    public void onClosing(WebSocket webSocket, int code, String reason) {
                        logger.info("WebSocket正在关闭: " + code + ", " + reason);
                        webSocket.close(1000, "客户端主动关闭");
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        logger.info("WebSocket已关闭: " + code + ", " + reason);
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        logger.error("WebSocket连接失败: " + t.getMessage());
                        t.printStackTrace();
                        if(response != null) {
                            logger.error("响应: " + response);
                        }
                        latch.countDown();
                    }
                });

                // 关闭OkHttp客户端
                client.dispatcher().executorService().shutdown();
            });
            thread.setDaemon(true);
            thread.start();
        }
        latch.await();
    }

    /**
     * 生成StartTranscription指令的JSON
     */
    private static String generateStartTranscriptionJson(String taskId, AudioFormat format) {
        Map<String, Object> message = new HashMap<>();
        
        // 构建header
        Map<String, Object> header = new HashMap<>();
        header.put("name", "StartTranscription");
        header.put("message_id", UUID.randomUUID().toString());
        header.put("task_id", taskId);
        header.put("namespace", "SpeechTranscriber"); // 与阿里云SDK保持一致
        header.put("appkey", "appkey"); // 添加appkey字段
        message.put("header", header);
        
        // 构建payload
        Map<String, Object> payload = new HashMap<>();
        
        // 参照阿里云SDK的参数格式
        payload.put("format", "pcm"); // 使用PCM编码
        payload.put("sample_rate", 16000); // 固定采样率为16kHz
        payload.put("enable_intermediate_result", true); // 启用中间结果
        payload.put("enable_punctuation_prediction", true); // 启用标点预测
        payload.put("enable_inverse_text_normalization", false); // 禁用ITN
        
        // 添加context字段
        Map<String, Object> context = new HashMap<>();
        message.put("context", context);
        
        message.put("payload", payload);
        
        return JacksonUtils.serialize(message);
    }

    /**
     * 生成StopTranscription指令的JSON
     */
    private static String generateStopTranscriptionJson(String taskId) {
        Map<String, Object> message = new HashMap<>();
        
        // 构建header
        Map<String, Object> header = new HashMap<>();
        header.put("name", "StopTranscription");
        header.put("message_id", UUID.randomUUID().toString());
        header.put("task_id", taskId);
        header.put("namespace", "SpeechTranscriber"); // 与阿里云SDK保持一致
        header.put("appkey", "appkey"); // 添加appkey字段
        message.put("header", header);
        
        // 添加context字段
        Map<String, Object> context = new HashMap<>();
        message.put("context", context);
        
        // 注意：阿里云SDK的stop方法不需要payload字段
        
        return JacksonUtils.serialize(message);
    }

    /**
     * 发送音频流
     */
    private static void sendAudioStream(WebSocket webSocket, String filePath) throws Exception {
        File audioFile = new File(filePath);
        byte[] audioData = Files.readAllBytes(audioFile.toPath());
        
        // 分块发送音频数据
        int chunkSize = 3200; // 每次发送约100ms的音频数据（16kHz, 16bit, 单声道）
        for (int i = 0; i < audioData.length; i += chunkSize) {
            if (!isRunning) {
                break;
            }
            
            int end = Math.min(i + chunkSize, audioData.length);
            byte[] chunk = new byte[end - i];
            System.arraycopy(audioData, i, chunk, 0, chunk.length);
            
            webSocket.send(ByteString.of(chunk));
            
            // 间隔100ms发送，模拟实时音频流
            Thread.sleep(100);
        }

        logger.info("音频数据发送完成");
    }
}
