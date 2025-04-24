package com.ke.bella.openapi.protocol.asr.flash;

import com.google.common.collect.Lists;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.asr.HuoshanLMRealTimeAsrResponse;
import com.ke.bella.openapi.protocol.asr.HuoshanProperty;
import com.ke.bella.openapi.protocol.asr.HuoshanRealTimeAsrRequest;
import com.ke.bella.openapi.protocol.asr.HuoshanStreamLMAsrCallback;
import com.ke.bella.openapi.utils.JacksonUtils;
import okhttp3.Request;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component("HuoshanLMFlashAsr")
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
    protected Callbacks.WebSocketCallback createCallback(
            HuoshanRealTimeAsrRequest huoshanRequest, Callbacks.Sender sender, EndpointProcessData processData) {
        return new HuoshanStreamLMAsrCallback(huoshanRequest, sender, processData, null, response -> {
            List<String> results = Lists.newArrayList();
            
            // 处理大模型ASR响应
            if (response.getResult() != null) {
                // 如果有分句信息
                if (response.getResult().getUtterances() != null && !response.getResult().getUtterances().isEmpty()) {
                    // 处理每个分句结果
                    for (HuoshanLMRealTimeAsrResponse.Utterance utterance : response.getResult().getUtterances()) {
                        if(utterance.isDefinite()) {
                            Text text = Text.builder()
                                    .beginTime(utterance.getStart_time())
                                    .endTime(utterance.getEnd_time())
                                    .text(utterance.getText())
                                    .build();
                            results.add(JacksonUtils.serialize(text));
                        }
                    }
                }
                // 如果只有完整文本
                else if (response.getResult().getText() != null && !response.getResult().getText().isEmpty()) {
                    Text text = Text.builder()
                            .beginTime(0)
                            .endTime(response.getDuration())
                            .text(response.getResult().getText())
                            .build();
                    results.add(JacksonUtils.serialize(text));
                }
            }
            
            return results;
        });
    }

    @Override
    public String getDescription() {
        return "火山大模型协议";
    }
}
