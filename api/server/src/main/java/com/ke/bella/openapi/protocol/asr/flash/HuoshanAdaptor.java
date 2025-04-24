package com.ke.bella.openapi.protocol.asr.flash;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.realtime.RealTimeMessage;
import okhttp3.WebSocket;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.BellaWebSocketListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.AsrRequest;
import com.ke.bella.openapi.protocol.asr.HuoshanProperty;
import com.ke.bella.openapi.protocol.asr.HuoshanRealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.HuoshanStreamAsrCallback;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Request;

@Component("HuoshanFlashAsr")
public class HuoshanAdaptor implements FlashAsrAdaptor<HuoshanProperty> {

    @Override
    public FlashAsrResponse asr(AsrRequest request, String url, HuoshanProperty property, EndpointProcessData processData) {
        HuoshanRealTimeAsrRequest huoshanRequest = new HuoshanRealTimeAsrRequest(request, property);
        CompletableFuture<List<String>> future = new CompletableFuture();
        Callbacks.Sender sender = buildSender(future);
        Callbacks.WebSocketCallback callback = createCallback(huoshanRequest, sender, processData);
        Request websocketRequest = createRequest(url, property);
        WebSocket webSocket = HttpUtils.websocketRequest(websocketRequest, new BellaWebSocketListener(callback));
        FlashAsrResponse response = responseConverter(future, processData);
        webSocket.close(1000, "client close");
        return response;
    }

    @Override
    public String getDescription() {
        return "火山协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return HuoshanProperty.class;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Text {
        private String text;
        private int beginTime;
        private int endTime;
    }

    protected Callbacks.WebSocketCallback createCallback(HuoshanRealTimeAsrRequest huoshanRequest, Callbacks.Sender sender, EndpointProcessData processData) {
        return new HuoshanStreamAsrCallback(huoshanRequest, sender, processData, null, response -> {
            if(response.getResult() == null) {
                return Lists.newArrayList();
            }
            return response.getResult().stream()
                    .map(result -> {
                        if(CollectionUtils.isEmpty(result.getUtterances())) {
                            return Lists.newArrayList(result);
                        } else {
                            return result.getUtterances();
                        }
                    }).flatMap(List::stream)
                    .map(result -> {
                        Text text = Text.builder().beginTime(result.getBeginTime())
                                .endTime(result.getEnd_time())
                                .text(result.getText())
                                .build();
                        return JacksonUtils.serialize(text);
                    }).collect(Collectors.toList());
        });
    }

    protected Request createRequest(String url, HuoshanProperty property) {
        return authorizationRequestBuilder(property.getAuth()).url(url).build();
    }

    protected Callbacks.Sender buildSender(CompletableFuture<List<String>> future) {
        return new Callbacks.Sender() {
            final List<String> texts = new ArrayList<>();
            @Override
            public void send(String text) {
                texts.add(text);
            }

            @Override
            public void send(byte[] bytes) {
            }

            @Override
            public void onError(Throwable e) {
                future.completeExceptionally(e);
            }

            @Override
            public void close() {
                if(!future.isDone()) {
                    future.complete(texts);
                }
            }
        };
    }

    private FlashAsrResponse responseConverter(CompletableFuture<List<String>> future, EndpointProcessData processData) {
        try {
            List<String> texts = future.get();
            FlashAsrResponse response = FlashAsrResponse.builder()
                    .taskId(processData.getChannelRequestId())
                    .flashResult(FlashAsrResponse.FlashResult.builder()
                            .duration(Integer.parseInt(processData.getMetrics().get("ttlt").toString()))
                            .sentences(new ArrayList<>())
                            .build())
                    .user(processData.getUser())
                    .build();
            for(String text : texts) {
                Text obj = JacksonUtils.deserialize(text, Text.class);
                response.getFlashResult().getSentences().add(FlashAsrResponse.Sentence.builder()
                        .beginTime(obj.getBeginTime())
                        .endTime(obj.getEndTime())
                        .text(obj.getText()).build());
            }
            return response;
        } catch (Exception e) {
            throw ChannelException.fromException(e);
        }
    }

}
