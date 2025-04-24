package com.ke.bella.openapi.protocol.asr.realtime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.HuoshanProperty;
import com.ke.bella.openapi.protocol.asr.HuoshanRealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.HuoshanRealTimeAsrResponse;
import com.ke.bella.openapi.protocol.asr.HuoshanStreamAsrCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import okhttp3.Request;
import okhttp3.WebSocket;

@Component("HuoshanRealtimeAsr")
public class HuoshanAdaptor implements RealTimeAsrAdaptor<HuoshanProperty> {

    @Override
    public WebSocket startTranscription(String url, HuoshanProperty property, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        WebSocket webSocket = HttpUtils.websocketRequest(createRequest(url, property), new BellaWebSocketListener(callback));
        callback.started();
        return webSocket;
    }

    protected Request createRequest(String url, HuoshanProperty property) {
        return authorizationRequestBuilder(property.getAuth()).url(url).build();
    }

    @Override
    public boolean sendAudioData(WebSocket webSocket, byte[] audioData, Callbacks.WebSocketCallback callback) {
        HuoshanStreamAsrCallback huoshanCallback = (HuoshanStreamAsrCallback) callback;
        huoshanCallback.sendAudioData(webSocket, audioData, false);
        return true;
    }

    @Override
    public boolean stopTranscription(WebSocket webSocket, RealTimeMessage request, Callbacks.WebSocketCallback callback) {
        HuoshanStreamAsrCallback huoshanCallback = (HuoshanStreamAsrCallback) callback;
        huoshanCallback.sendAudioData(webSocket, null, true);
        return true;
    }

    @Override
    public void closeConnection(WebSocket webSocket) {
        webSocket.close(1000, "client close");
    }

    @Override
    public Callbacks.WebSocketCallback createCallback(Callbacks.Sender sender, EndpointProcessData processData, EndpointLogger logger,
            String taskId, RealTimeMessage request, HuoshanProperty property) {
        HuoshanRealTimeAsrRequest huoshanRealTimeAsrRequest = new HuoshanRealTimeAsrRequest(request, property);
        return new HuoshanStreamAsrCallback(huoshanRealTimeAsrRequest, sender, processData, logger, new Converter(taskId));
    }

    @Override
    public String getDescription() {
        return "火山协议";
    }

    @Override
    public Class<HuoshanProperty> getPropertyClass() {
        return HuoshanProperty.class;
    }

    private static class Converter implements Function<HuoshanRealTimeAsrResponse, List<String>> {

        private final String taskId;

        boolean sentenceStart = true;

        int index = -1;

        public Converter(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public List<String> apply(HuoshanRealTimeAsrResponse response) {
            List<String> result = new ArrayList<>();
            if(response.getResult() != null) {
                for(HuoshanRealTimeAsrResponse.Result tempResult : response.getResult()) {
                    if(CollectionUtils.isEmpty(tempResult.getUtterances())) {
                        continue;
                    }
                    if(sentenceStart) {
                        index++;
                        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                        payload.setIndex(index);
                        payload.setTime(response.getAddition() != null ? response.getAddition().getDuration() : 0);
                        RealTimeMessage start = RealTimeMessage.sentenceBegin(taskId, payload);
                        result.add(JacksonUtils.serialize(start));
                        sentenceStart = false;
                    }
                    for(HuoshanRealTimeAsrResponse.Result huoshanResult : tempResult.getUtterances()) {
                        RealTimeMessage.Payload payload = new RealTimeMessage.Payload();
                        payload.setIndex(index);
                        payload.setTime(huoshanResult.getTime());
                        payload.setResult(huoshanResult.getText());
                        payload.setConfidence(huoshanResult.getConfidence());
                        if(huoshanResult.getWords() != null) {
                            payload.setWords(huoshanResult.getWords().stream().map(HuoshanRealTimeAsrResponse.Result.Word::convert)
                                    .collect(Collectors.toList()));
                        }
                        if(huoshanResult.isDefinite()) {
                            payload.setBeginTime(huoshanResult.getBegin_time());
                            RealTimeMessage end = RealTimeMessage.sentenceEnd(taskId, payload);
                            result.add(JacksonUtils.serialize(end));
                            sentenceStart = true;
                        } else {
                            RealTimeMessage changed = RealTimeMessage.resultChange(taskId, payload);
                            result.add(JacksonUtils.serialize(changed));
                        }
                    }
                }
            }
            if(response.getSequence() < 0) {
                RealTimeMessage completion = RealTimeMessage.completion(taskId);
                result.add(JacksonUtils.serialize(completion));
            }
            return result;
        }
    }
}
