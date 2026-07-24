package com.ke.bella.openapi;

import com.ke.bella.openapi.protocol.realtime.RealTimeEventType;
import com.ke.bella.openapi.protocol.realtime.RealTimeHeader;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 实时转录WebSocket Demo 用于与/v1/audio/realtime服务进行交互
 */
public class RealtimeDemo {
    private static final Logger logger = LoggerFactory.getLogger(RealtimeDemo.class);

    // WebSocket服务器地址
    private static final String WEBSOCKET_URL = "ws://localhost:8080/v1/audio/realtime";
    //替换为您的apikey
    private static final String TOKEN = "Bearer **********";
    private static final String APPKEY = "demo";
    //替换为您的文件目录
    private static final String AUDIO_FILE_PATH = "sample_audio.pcm"; // replace with your audio file path

    // 用于同步的锁
    private static final CountDownLatch latch = new CountDownLatch(1);

    // 是否正在运行
    private static boolean isRunning = false;
    private static String taskId;

    public static void main(String[] args) throws Exception {
        logger.info("启动RealtimeDemo...");

        Request request = new Request.Builder()
                .url(WEBSOCKET_URL)
                .header("Authorization", TOKEN)
                .build();

        // 配置OkHttp客户端
        OkHttpClient client = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        // 生成任务ID - 使用UUID并移除破折号，与前端保持一致
        taskId = UUID.randomUUID().toString().replace("-", "");
        logger.info("任务ID: {}", taskId);

        // 建立WebSocket连接
        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                logger.info("WebSocket连接已建立");

                // 发送StartTranscription指令 - 与前端保持一致的消息格式
                String startTranscriptionJson = generateStartTranscriptionJson();
                webSocket.send(startTranscriptionJson);
                logger.info("已发送StartTranscription指令: {}", startTranscriptionJson);

                // 开始发送音频数据
                Thread audioThread = new Thread(() -> {
                    try {
                        // 等待TranscriptionStarted事件
                        Thread.sleep(1000);

                        // 发送音频数据
                        sendSampleAudioData(webSocket);
                    } catch (Exception e) {
                        logger.error("发送音频数据出错: {}", e.getMessage(), e);
                    }
                });
                audioThread.setDaemon(true);
                audioThread.start();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                logger.info("收到文本消息: {}", text);

                // 根据前端的处理方式，解析消息并处理特定事件
                try {
                    if(text.contains("TranscriptionStarted")) {
                        logger.info("转录已开始");
                        isRunning = true;
                    } else if(text.contains("TranscriptionResultChanged")) {
                        logger.info("转录结果更新");
                    } else if(text.contains("TranscriptionCompleted")) {
                        logger.info("转录已完成");
                        isRunning = false;
                        latch.countDown();
                    } else if(text.contains("SentenceBegin")) {
                        logger.info("句子开始");
                    } else if(text.contains("SentenceEnd")) {
                        logger.info("句子结束");
                    } else if(text.contains("TaskFailed") || text.contains("TranscriptionFailed") || text.contains("LLM_CHAT_ERROR")) {
                        logger.error("任务失败");
                        isRunning = false;
                        latch.countDown();
                    } else if(text.contains("LLM_CHAT_END")) {
                        logger.info("LLM聊天结束");
                    } else if(text.contains("LLM_CHAT_DELTA")) {
                        logger.info("收到LLM聊天增量数据");
                    } else if(text.contains("TTS_BEGIN")) {
                        logger.info("TTS开始");
                    } else if(text.contains("TTS_TTFT")) {
                        logger.info("收到TTS首包时间信息");
                    } else if(text.contains("TTS_END")) {
                        logger.info("TTS结束，所有处理完成");
                        // 发送StopTranscription指令结束任务
                        String stopTranscriptionJson = generateStopTranscriptionJson();
                        webSocket.send(stopTranscriptionJson);
                        logger.info("已发送StopTranscription指令: {}", stopTranscriptionJson);
                        Thread.sleep(2000);
                        isRunning = false;
                        latch.countDown();
                    }
                } catch (Exception e) {
                    logger.error("解析消息失败: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                logger.info("收到二进制消息，长度: {}", bytes.size());

                // 处理TTS音频数据
                try {
                    // 保存TTS音频数据到文件
                    File outputDir = new File("tts_output");
                    if(!outputDir.exists()) {
                        outputDir.mkdirs();
                    }

                    File outputFile = new File(outputDir, "tts_" + System.currentTimeMillis() + ".pcm");
                    try (FileOutputStream fos = new FileOutputStream(outputFile, false)) {
                        fos.write(bytes.toByteArray());
                    }
                    logger.info("TTS音频数据已保存到文件: {}", outputFile.getAbsolutePath());
                } catch (IOException e) {
                    logger.error("保存TTS音频数据时出错: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                logger.info("WebSocket正在关闭: {}, {}", code, reason);
                webSocket.close(1000, "客户端主动关闭");
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                logger.info("WebSocket已关闭: {}, {}", code, reason);
                latch.countDown();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                logger.error("WebSocket连接失败: {}", t.getMessage(), t);
                if(response != null) {
                    logger.error("响应: {}", response);
                }
                latch.countDown();
            }
        });

        // 等待任务完成
        try {
            latch.await();
            logger.info("Demo执行完毕");
        } catch (InterruptedException e) {
            logger.error("等待中断: {}", e.getMessage(), e);
        }

        // 关闭OkHttp客户端
        client.dispatcher().executorService().shutdown();
    }

    /**
     * 生成StartTranscription指令的JSON - 使用RealTimeMessage类
     */
    private static String generateStartTranscriptionJson() {
        RealTimeMessage message = new RealTimeMessage();

        // 创建消息头
        RealTimeHeader header = new RealTimeHeader();
        header.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        header.setTaskId(taskId);
        header.setNamespace("SpeechTranscriber");
        header.setName(RealTimeEventType.START_TRANSCRIPTION.getValue());
        header.setAppkey(APPKEY);
        message.setHeader(header);

        // 创建消息负载
        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
        payload.setFormat("pcm");
        payload.setSampleRate(16000);
        payload.setEnableIntermediateResult(true);
        payload.setEnablePunctuationPrediction(true);
        payload.setEnableInverseTextNormalization(true);

        //llm
        RealTimeMessage.LlmOption llmOption = new RealTimeMessage.LlmOption();
        RealTimeMessage.MainLlmOption mainLlmOption = new RealTimeMessage.MainLlmOption();
        mainLlmOption.setSysPrompt("你是一个全能的语音助理，你的回复会转成音频给用户，所以请尽可能简洁的回复，同时首句话尽快结束以便更好的进行流式合成语音。");
        llmOption.setMain(mainLlmOption);
        payload.setLlmOption(llmOption);

        //tts
        RealTimeMessage.TtsOption ttsOption = new RealTimeMessage.TtsOption();
        ttsOption.setSampleRate(24000);
        ttsOption.setVoice("28");
        payload.setTtsOption(ttsOption);

        message.setPayload(payload);

        return JacksonUtils.serialize(message);
    }

    /**
     * 生成StopTranscription指令的JSON - 使用RealTimeMessage类
     */
    private static String generateStopTranscriptionJson() {
        RealTimeMessage message = new RealTimeMessage();

        // 创建消息头
        RealTimeHeader header = new RealTimeHeader();
        header.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        header.setTaskId(taskId);
        header.setNamespace("SpeechTranscriber");
        header.setName(RealTimeEventType.STOP_TRANSCRIPTION.getValue());
        header.setAppkey(APPKEY);
        message.setHeader(header);

        // 创建空的消息负载
        message.setPayload(new RealTimeMessage.Payload());

        return JacksonUtils.serialize(message);
    }

    /**
     * 发送音频数据 从文件中读取音频数据并发送
     */
    private static void sendSampleAudioData(WebSocket webSocket) throws Exception {
        // 检查文件是否存在
        File audioFile = new File(AUDIO_FILE_PATH);
        if(!audioFile.exists()) {
            logger.error("音频文件不存在: {}", AUDIO_FILE_PATH);
            return;
        }

        logger.info("开始发送音频数据：'{}'", AUDIO_FILE_PATH);

        // 读取音频文件
        try (FileInputStream fis = new FileInputStream(audioFile)) {
            byte[] buffer = new byte[3200]; // 100ms的16kHz, 16bit音频
            int bytesRead;
            int totalBytes = 0;
            int frameCount = 0;

            while ((bytesRead = fis.read(buffer)) != -1) {
                if(!isRunning) {
                    logger.info("转录已停止，终止发送音频");
                    break;
                }

                // 发送音频数据
                webSocket.send(ByteString.of(buffer, 0, bytesRead));

                // 更新统计信息
                totalBytes += bytesRead;
                frameCount++;

                // 每10帧记录一次进度
                if(frameCount % 10 == 0) {
                    logger.info("已发送 {} 帧音频数据，共 {} 字节", frameCount, totalBytes);
                }

                // 间隔100ms发送，模拟实时音频流
                Thread.sleep(100);
            }

            logger.info("音频数据'{}'发送完成，共发送 {} 帧，{} 字节", AUDIO_FILE_PATH, frameCount, totalBytes);

            // 发送静音帧以触发VOICE_QUIET事件
            logger.info("开始发送静音帧以触发VOICE_QUIET事件...");
            byte[] silenceBuffer = new byte[3200]; // 全0的缓冲区代表静音

            // 发送多个静音帧，确保触发VOICE_QUIET事件
            for (int i = 0; i < 20; i++) {
                webSocket.send(ByteString.of(silenceBuffer));
                logger.info("已发送第 {} 帧静音数据", i + 1);
            }

            logger.info("静音帧发送完成，等待服务器处理...");

        } catch (IOException e) {
            logger.error("读取或发送音频数据时出错: {}", e.getMessage(), e);
            throw new Exception("音频数据处理失败", e);
        }
    }
}
