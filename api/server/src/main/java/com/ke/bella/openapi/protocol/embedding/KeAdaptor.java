package com.ke.bella.openapi.protocol.embedding;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import okhttp3.Request;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("KeEmbedding")
public class KeAdaptor extends OpenAIAdaptor {
    protected EmbeddingResponse doRequest(Request httpRequest) {
        KeEmbeddingResponse keResponse = HttpUtils.httpRequest(httpRequest, KeEmbeddingResponse.class);
        EmbeddingResponse response = new EmbeddingResponse();
        if(CollectionUtils.isEmpty(keResponse.getEmbed_res())) {
            int httpCode = keResponse.getCode() > 200 && keResponse.getCode() < 600 ? keResponse.getCode() : 500;
            response.setError(OpenapiResponse.OpenapiError.builder()
                    .httpCode(httpCode)
                    .message(keResponse.getMsg())
                    .type(HttpStatus.valueOf(httpCode).getReasonPhrase())
                    .build());
        } else {
            response.setObject("list");
            List<EmbeddingResponse.EmbeddingData> data = new ArrayList<>();
            for (int i = 0; i < keResponse.getEmbed_res().size(); i++) {
                EmbeddingResponse.EmbeddingData embeddingData = new EmbeddingResponse.EmbeddingData();
                embeddingData.setEmbedding(keResponse.getEmbed_res().get(i));
                embeddingData.setIndex(i);
                embeddingData.setObject("embedding");
                data.add(embeddingData);
            }
            response.setData(data);
        }
        return response;
    }

    @Override
    public String getDescription() {
        return "贝壳私有协议（未实现OpenAI协议）";
    }
}
