package com.ke.bella.openapi.protocol.asr.realtime;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.TencentV2Property;
import com.ke.bella.openapi.protocol.asr.TencentV2RealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.TencentV2RealTimeAsrResponse;
import com.ke.bella.openapi.protocol.asr.TencentV2StreamAsrCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.WebSocket;

@Slf4j
@Component("TencentV2RealtimeAsr")
public class TencentV2Adaptor implements RealTimeAsrAdaptor<TencentV2Property> {

    @Override
    public WebSocket startTranscription(String url, TencentV2Property property, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        String wsUrl = buildWebSocketUrl(url, property, ((TencentV2StreamAsrCallback) callback).getRequest());
        Request wsRequest = new Request.Builder().url(wsUrl).build();
        WebSocket webSocket = HttpUtils.websocketRequest(wsRequest, new BellaWebSocketListener(callback));
        callback.started();
        return webSocket;
    }

    @Override
    public boolean sendAudioData(WebSocket webSocket, byte[] audioData, Callbacks.WebSocketCallback callback) {
        ((TencentV2StreamAsrCallback) callback).sendAudioData(webSocket, audioData);
        return true;
    }

    @Override
    public boolean stopTranscription(WebSocket webSocket, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        ((TencentV2StreamAsrCallback) callback).sendEnd(webSocket);
        return true;
    }

    @Override
    public void closeConnection(WebSocket webSocket) {
        webSocket.close(1000, "client close");
    }

    @Override
    public Callbacks.WebSocketCallback createCallback(Callbacks.Sender sender, EndpointProcessData processData, EndpointLogger logger,
            String taskId, RealTimeMessage request, TencentV2Property property) {
        TencentV2RealTimeAsrRequest tencentRequest = new TencentV2RealTimeAsrRequest(request, property);
        return new TencentV2StreamAsrCallback(tencentRequest, sender, processData, logger, new Converter(taskId));
    }

    @Override
    public String getDescription() {
        return "腾讯V2协议";
    }

    @Override
    public Class<TencentV2Property> getPropertyClass() {
        return TencentV2Property.class;
    }

    String buildWebSocketUrl(String baseUrl, TencentV2Property property, TencentV2RealTimeAsrRequest request) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            long expired = timestamp + 86400 * 90 - 1;
            int nonce = ThreadLocalRandom.current().nextInt(1, 1000000000);

            Map<String, String> params = new TreeMap<>();
            put(params, "secretid", property.getAuth().getApiKey());
            put(params, "timestamp", String.valueOf(timestamp));
            put(params, "expired", String.valueOf(expired));
            put(params, "nonce", String.valueOf(nonce));
            put(params, "engine_model_type", request.getEngineModelType());
            put(params, "voice_id", request.getVoiceId());
            put(params, "needvad", String.valueOf(request.getNeedvad() == null ? 1 : request.getNeedvad()));
            put(params, "voice_format", String.valueOf(request.getVoiceFormat()));
            put(params, "input_sample_rate", toString(request.getInputSampleRate()));
            put(params, "hotword_list", request.getHotwordList());
            put(params, "hotword_id", request.getHotwordId());
            put(params, "customization_id", request.getCustomizationId());
            put(params, "replace_text_id", request.getReplaceTextId());
            put(params, "filter_dirty", toString(request.getFilterDirty()));
            put(params, "filter_modal", toString(request.getFilterModal()));
            put(params, "filter_empty_result", toString(request.getFilterEmptyResult()));
            put(params, "convert_num_mode", toString(request.getConvertNumMode()));
            put(params, "noise_threshold", toString(request.getNoiseThreshold()));
            put(params, "sentence_strategy", toString(request.getSentenceStrategy()));
            put(params, "domain", toString(request.getDomain()));
            put(params, "enable_speaker_context", toString(request.getEnableSpeakerContext()));
            put(params, "speaker_context_id", request.getSpeakerContextId());

            params.put("signature", generateSignature(property.getAppid(), params, property.getAuth().getSecret()));
            String queryString = params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + urlEncode(e.getValue()))
                    .collect(Collectors.joining("&"));
            String finalUrl = StringUtils.stripEnd(baseUrl, "/") + "/" + property.getAppid() + "?" + queryString;
            log.info("Tencent ASR V2 WebSocket URL built: appid={}, voice_id={}", property.getAppid(), request.getVoiceId());
            return finalUrl;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Tencent ASR V2 WebSocket URL", e);
        }
    }

    String generateSignature(String appid, Map<String, String> params, String secretKey) throws Exception {
        String queryString = params.entrySet().stream()
                .filter(e -> !"signature".equals(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        String signatureText = "asr.cloud.tencent.com/asr/v2/" + appid + "?" + queryString;
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(signatureText.getBytes(StandardCharsets.UTF_8)));
    }

    private void put(Map<String, String> params, String key, String value) {
        if(StringUtils.isNotBlank(value)) {
            params.put(key, value);
        }
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static class Converter implements Function<TencentV2RealTimeAsrResponse, List<String>> {
        private final String taskId;
        private final Set<Integer> sentenceStarted = new HashSet<>();

        public Converter(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public List<String> apply(TencentV2RealTimeAsrResponse response) {
            List<String> result = new ArrayList<>();
            TencentV2RealTimeAsrResponse.Sentences sentences = response.getSentences();
            if(sentences != null && sentences.getSentence_list() != null) {
                for (TencentV2RealTimeAsrResponse.Sentence sentence : sentences.getSentence_list()) {
                    Integer index = sentence.getSentence_id() == null ? 0 : sentence.getSentence_id();
                    if(sentenceStarted.add(index)) {
                        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                        payload.setIndex(index);
                        payload.setTime(sentence.getStart_time());
                        result.add(JacksonUtils.serialize(RealTimeMessage.sentenceBegin(taskId, payload)));
                    }

                    RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                    payload.setIndex(index);
                    if (sentence.getStart_time() != null && sentence.getEnd_time() != null
                            && sentence.getEnd_time() >= sentence.getStart_time()) {
                        payload.setBeginTime(sentence.getStart_time());
                        payload.setTime(sentence.getEnd_time());
                    }
                    payload.setResult(sentence.getSentence());
                    Map<String, Object> additions = new HashMap<>();
                    additions.put("speaker_id", sentence.getSpeaker_id());
                    additions.put("voice_id", response.getVoice_id());
                    additions.put("message_id", response.getMessage_id());
                    payload.setAdditions(additions);

                    if(sentence.isDefinite()) {
                        result.add(JacksonUtils.serialize(RealTimeMessage.sentenceEnd(taskId, payload)));
                    } else if(sentence.isIntermediate()) {
                        result.add(JacksonUtils.serialize(RealTimeMessage.resultChange(taskId, payload)));
                    }
                }
            }
            if(response.isFinal()) {
                result.add(JacksonUtils.serialize(RealTimeMessage.completion(taskId)));
            }
            return result;
        }
    }
}
