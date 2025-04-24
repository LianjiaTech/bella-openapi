package com.ke.bella.openapi.protocol.asr.realtime;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.HuoshanLMRealTimeAsrResponse;
import com.ke.bella.openapi.protocol.asr.HuoshanProperty;
import com.ke.bella.openapi.protocol.asr.HuoshanRealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.HuoshanStreamAsrCallback;
import com.ke.bella.openapi.protocol.asr.HuoshanStreamLMAsrCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import okhttp3.WebSocket;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 火山引擎大模型流式语音识别适配器
 */
@Component("HuoshanLMRealtimeAsr")
public class HuoshanLMAdaptor extends HuoshanAdaptor {


    @Override
    protected Request createRequest(String url, HuoshanProperty property) {
        return new Request.Builder()
                .url(url)
                .header("X-Api-App-Key", property.getAppid())
                .header("X-Api-Access-Key", property.getAuth().getApiKey())
                .header("X-Api-Resource-Id", property.getDeployName())
                .header("X-Api-Connect-Id", UUID.randomUUID().toString())
                .build();
    }


    @Override
    public Callbacks.WebSocketCallback createCallback(Callbacks.Sender sender, EndpointProcessData processData, EndpointLogger logger,
            String taskId, RealTimeMessage request, HuoshanProperty property) {
        HuoshanRealTimeAsrRequest huoshanRealTimeAsrRequest = new HuoshanRealTimeAsrRequest(request, property);
        return new HuoshanStreamLMAsrCallback(huoshanRealTimeAsrRequest, sender, processData, logger, new Converter(taskId));
    }

    @Override
    public boolean sendAudioData(WebSocket webSocket, byte[] audioData, Callbacks.WebSocketCallback callback) {
        HuoshanStreamLMAsrCallback huoshanCallback = (HuoshanStreamLMAsrCallback) callback;
        huoshanCallback.sendAudioData(webSocket, audioData, false);
        return true;
    }

    @Override
    public boolean stopTranscription(WebSocket webSocket, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        HuoshanStreamLMAsrCallback huoshanCallback = (HuoshanStreamLMAsrCallback) callback;
        huoshanCallback.sendAudioData(webSocket, null, true);
        return true;
    }

    @Override
    public String getDescription() {
        return "火山大模型协议";
    }

    private static class Converter implements Function<HuoshanLMRealTimeAsrResponse, List<String>> {

        private final String taskId;

        boolean sentenceStart = true;

        int index = -1;

        public Converter(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public List<String> apply(HuoshanLMRealTimeAsrResponse response) {
            List<String> result = new ArrayList<>();

            // 处理结果
            if (response.getResult() != null) {
                // 检查是否有分句信息
                boolean hasUtterances = response.getResult().getUtterances() != null && !response.getResult().getUtterances().isEmpty();

                // 如果有分句信息，处理每个分句
                if (hasUtterances) {
                    if (sentenceStart) {
                        index++;
                        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                        payload.setIndex(index);
                        payload.setTime(response.getDuration());
                        RealTimeMessage start = RealTimeMessage.sentenceBegin(taskId, payload);
                        result.add(JacksonUtils.serialize(start));
                        sentenceStart = false;
                    }

                    // 处理每个分句结果
                    for (HuoshanLMRealTimeAsrResponse.Utterance utterance : response.getResult().getUtterances()) {
                        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                        payload.setIndex(index);
                        payload.setTime(utterance.getEnd_time() - utterance.getStart_time());
                        payload.setResult(utterance.getText());
                        payload.setConfidence(utterance.getConfidence());

                        if (utterance.getWords() != null) {
                            payload.setWords(utterance.getWords().stream()
                                    .map(HuoshanLMRealTimeAsrResponse.Word::convert)
                                    .collect(Collectors.toList()));
                        }

                        if (utterance.isDefinite()) {
                            payload.setBeginTime(utterance.getStart_time());
                            RealTimeMessage end = RealTimeMessage.sentenceEnd(taskId, payload);
                            result.add(JacksonUtils.serialize(end));
                            sentenceStart = true;
                        } else {
                            RealTimeMessage changed = RealTimeMessage.resultChange(taskId, payload);
                            result.add(JacksonUtils.serialize(changed));
                        }
                    }
                }
                // 如果没有分句信息但有完整文本，也生成一个结果
                else if (response.getResult().getText() != null && !response.getResult().getText().isEmpty()) {
                    if (sentenceStart) {
                        index++;
                        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                        payload.setIndex(index);
                        payload.setTime(response.getDuration());
                        RealTimeMessage start = RealTimeMessage.sentenceBegin(taskId, payload);
                        result.add(JacksonUtils.serialize(start));
                    }

                    RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                    payload.setIndex(index);
                    payload.setResult(response.getResult().getText());
                    payload.setTime(response.getDuration());

                    RealTimeMessage end = RealTimeMessage.sentenceEnd(taskId, payload);
                    result.add(JacksonUtils.serialize(end));
                    sentenceStart = true;
                }
            }

            // 处理最终结束信号
            if (response.getSequence() < 0) {
                RealTimeMessage completion = RealTimeMessage.completion(taskId);
                result.add(JacksonUtils.serialize(completion));
            }

            return result;
        }
    }
}
