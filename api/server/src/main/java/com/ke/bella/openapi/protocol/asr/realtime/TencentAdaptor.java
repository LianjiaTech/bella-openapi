package com.ke.bella.openapi.protocol.asr.realtime;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.TencentProperty;
import com.ke.bella.openapi.protocol.asr.TencentRealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.TencentRealTimeAsrResponse;
import com.ke.bella.openapi.protocol.asr.TencentStreamAsrCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.WebSocket;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 腾讯实时语音识别适配器
 */
@Slf4j
@Component("TencentRealtimeAsr")
public class TencentAdaptor implements RealTimeAsrAdaptor<TencentProperty> {

    @Override
    public WebSocket startTranscription(String url, TencentProperty property, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        // 构建完整的WebSocket URL（包含签名）
        String wsUrl = buildWebSocketUrl(url, property, (TencentStreamAsrCallback) callback);

        // 创建WebSocket请求
        Request wsRequest = new Request.Builder()
                .url(wsUrl)
                .build();

        // 建立WebSocket连接
        WebSocket webSocket = HttpUtils.websocketRequest(wsRequest, new com.ke.bella.openapi.protocol.BellaWebSocketListener(callback));
        callback.started();
        return webSocket;
    }

    @Override
    public boolean sendAudioData(WebSocket webSocket, byte[] audioData, Callbacks.WebSocketCallback callback) {
        TencentStreamAsrCallback tencentCallback = (TencentStreamAsrCallback) callback;
        tencentCallback.sendAudioData(webSocket, audioData);
        return true;
    }

    @Override
    public boolean stopTranscription(WebSocket webSocket, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        TencentStreamAsrCallback tencentCallback = (TencentStreamAsrCallback) callback;
        tencentCallback.sendEnd(webSocket);
        return true;
    }

    @Override
    public void closeConnection(WebSocket webSocket) {
        webSocket.close(1000, "client close");
    }

    @Override
    public Callbacks.WebSocketCallback createCallback(
            Callbacks.Sender sender,
            EndpointProcessData processData,
            EndpointLogger logger,
            String taskId,
            RealTimeMessage request,
            TencentProperty property) {
        TencentRealTimeAsrRequest tencentRequest = new TencentRealTimeAsrRequest(request, property);
        return new TencentStreamAsrCallback(tencentRequest, sender, processData, logger, new Converter(taskId));
    }

    @Override
    public String getDescription() {
        return "腾讯协议";
    }

    @Override
    public Class<TencentProperty> getPropertyClass() {
        return TencentProperty.class;
    }

    /**
     * 构建WebSocket URL，包含所有请求参数和签名
     */
    private String buildWebSocketUrl(String baseUrl, TencentProperty property, TencentStreamAsrCallback callback) {
        try {
            // 从TencentStreamAsrCallback中获取request对象
            TencentRealTimeAsrRequest request = callback.getRequest();

            // 当前时间戳
            long timestamp = System.currentTimeMillis() / 1000;
            // 过期时间（90天后）
            long expired = timestamp + 86400 * 90;
            // 随机数
            int nonce = new Random().nextInt(1000000000);

            // 构建参数Map（用于排序）
            Map<String, String> params = new TreeMap<>();
            params.put("secretid", property.getAuth().getApiKey());
            params.put("timestamp", String.valueOf(timestamp));
            params.put("expired", String.valueOf(expired));
            params.put("nonce", String.valueOf(nonce));
            params.put("engine_model_type", property.getEngineModelType());
            params.put("voice_id", request.getVoiceId());
            params.put("needvad", "1");

            // 可选参数
            if(request.getVoiceFormat() != null) {
                params.put("voice_format", String.valueOf(request.getVoiceFormat()));
            }

            if(request.getHotwordList() != null && !request.getHotwordList().isEmpty()) {
                params.put("hotword_list", request.getHotwordList());
            }
            if(request.getHotwordId() != null && !request.getHotwordId().isEmpty()) {
                params.put("hotword_id", request.getHotwordId());
            }
            if(request.getReplaceTextId() != null && !request.getReplaceTextId().isEmpty()) {
                params.put("replace_text_id", request.getReplaceTextId());
            }
            if(request.getWordInfo() != null) {
                params.put("word_info", String.valueOf(request.getWordInfo()));
            }

            // 生成签名
            String signature = generateSignature(property.getAppid(), params, property.getAuth().getSecret());
            params.put("signature", signature);

            // 构建完整URL
            String queryString = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + urlEncode(e.getValue()))
                    .collect(Collectors.joining("&"));

            // 腾讯ASR的WebSocket地址格式：wss://asr.cloud.tencent.com/asr/v2/<appid>?{params}
            String wsUrl = baseUrl + "/" + property.getAppid();
            String finalUrl = wsUrl + "?" + queryString;
            log.info("Tencent ASR WebSocket URL: {}", finalUrl);
            return finalUrl;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build WebSocket URL", e);
        }
    }

    /**
     * 生成签名
     * <p>
     * 签名原文格式：asr.cloud.tencent.com/asr/v2/<appid>?key1=value1&key2=value2...
     * 签名方法：HmacSHA1
     */
    private String generateSignature(String appid, Map<String, String> params, String secretKey) throws Exception {
        // 构建签名原文（除去signature参数）
        String queryString = params.entrySet().stream()
                .filter(e -> !"signature".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        String signatureText = "asr.cloud.tencent.com/asr/v2/" + appid + "?" + queryString;

        // 使用HMAC-SHA1加密
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(signatureText.getBytes(StandardCharsets.UTF_8));

        // Base64编码
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * URL编码
     */
    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 响应转换器：将腾讯ASR响应转换为标准RealTimeMessage格式
     */
    private static class Converter implements Function<TencentRealTimeAsrResponse, List<String>> {
        private final String taskId;
        private int currentIndex = -1;

        public Converter(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public List<String> apply(TencentRealTimeAsrResponse response) {
            List<String> result = new ArrayList<>();

            if(response.getResult() == null) {
                return result;
            }

            TencentRealTimeAsrResponse.Result asrResult = response.getResult();

            // slice_type: 0-开始，1-识别中，2-识别结束
            if(asrResult.getSlice_type() == null) {
                return result;
            }

            // 开始新的一段话
            if(asrResult.isBegin()) {
                currentIndex++;
                RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                payload.setIndex(currentIndex);
                payload.setTime(asrResult.getDuration());
                RealTimeMessage start = RealTimeMessage.sentenceBegin(taskId, payload);
                result.add(JacksonUtils.serialize(start));
            }
            // 识别中或识别结束
            else if(asrResult.isIntermediate() || asrResult.isDefinite()) {
                RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                payload.setIndex(asrResult.getIndex() != null ? asrResult.getIndex() : currentIndex);
                payload.setTime(asrResult.getDuration());
                payload.setResult(asrResult.getVoice_text_str());

                // 转换词列表
                if(asrResult.getWord_list() != null && !asrResult.getWord_list().isEmpty()) {
                    payload.setWords(asrResult.getWord_list().stream()
                            .map(TencentRealTimeAsrResponse.Result.Word::convert)
                            .collect(Collectors.toList()));
                }

                // 如果是识别结束（稳定结果）
                if(asrResult.isDefinite()) {
                    payload.setBeginTime(asrResult.getStart_time());
                    RealTimeMessage end = RealTimeMessage.sentenceEnd(taskId, payload);
                    result.add(JacksonUtils.serialize(end));
                } else {
                    // 识别中（非稳定结果）
                    RealTimeMessage changed = RealTimeMessage.resultChange(taskId, payload);
                    result.add(JacksonUtils.serialize(changed));
                }
            }

            // 如果是最终消息，发送完成信号
            if(response.isFinal()) {
                RealTimeMessage completion = RealTimeMessage.completion(taskId);
                result.add(JacksonUtils.serialize(completion));
            }

            return result;
        }
    }
}
