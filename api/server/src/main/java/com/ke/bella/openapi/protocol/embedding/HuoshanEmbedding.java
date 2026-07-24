package com.ke.bella.openapi.protocol.embedding;

import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.Data;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component("HuoshanEmbedding")
public class HuoshanEmbedding implements EmbeddingAdaptor<HuoshanEmbeddingProperty> {

    @Override
    public EmbeddingResponse embedding(EmbeddingRequest request, String url, HuoshanEmbeddingProperty property) {
        Request httpRequest = buildRequest(request, url, property);
        clearLargeData(request);
        return doRequest(httpRequest);
    }

    Request buildRequest(EmbeddingRequest request, String url, HuoshanEmbeddingProperty property) {
        if(property != null && StringUtils.isNotBlank(property.getDeployName())) {
            request.setModel(property.getDeployName());
        }
        return authorizationRequestBuilder(property == null ? null : property.getAuth())
                .url(url)
                .post(RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request)))
                .build();
    }

    protected EmbeddingResponse doRequest(Request httpRequest) {
        HuoshanEmbeddingResponse response = HttpUtils.httpRequest(httpRequest, HuoshanEmbeddingResponse.class, ((huoshanResponse, httpResponse) -> {
            if(huoshanResponse.getError() != null) {
                huoshanResponse.getError().setHttpCode(httpResponse.code());
            }
        }));
        return convertResponse(response);
    }

    EmbeddingResponse convertResponse(HuoshanEmbeddingResponse huoshanResponse) {
        if(huoshanResponse == null) {
            return null;
        }
        EmbeddingResponse response = new EmbeddingResponse();
        response.setError(huoshanResponse.getError());
        response.setSensitives(huoshanResponse.getSensitives());
        response.setRequestRiskData(huoshanResponse.getRequestRiskData());
        response.setChannelCode(huoshanResponse.getChannelCode());
        response.setId(huoshanResponse.getId());
        response.setCreated(huoshanResponse.getCreated());
        response.setObject(huoshanResponse.getObject());
        response.setModel(huoshanResponse.getModel());
        response.setUsage(huoshanResponse.getUsage());
        response.setData(convertData(huoshanResponse.getData()));
        return response;
    }

    private List<EmbeddingResponse.EmbeddingData> convertData(Object data) {
        if(data == null) {
            return null;
        }
        List<EmbeddingResponse.EmbeddingData> result = new ArrayList<>();
        if(data instanceof List) {
            List<?> dataList = (List<?>) data;
            for (int i = 0; i < dataList.size(); i++) {
                result.add(convertDataItem(dataList.get(i), i));
            }
        } else {
            result.add(convertDataItem(data, 0));
        }
        return result;
    }

    private EmbeddingResponse.EmbeddingData convertDataItem(Object item, int index) {
        EmbeddingResponse.EmbeddingData embeddingData = JacksonUtils.deserialize(JacksonUtils.serialize(item), EmbeddingResponse.EmbeddingData.class);
        if(embeddingData == null) {
            embeddingData = new EmbeddingResponse.EmbeddingData();
        }
        if(StringUtils.isBlank(embeddingData.getObject())) {
            embeddingData.setObject("embedding");
        }
        if(embeddingData.getIndex() == 0 && index > 0) {
            embeddingData.setIndex(index);
        }
        return embeddingData;
    }

    @Override
    public String getDescription() {
        return "火山方舟多模态Embedding协议";
    }

    @Override
    public Class<?> getPropertyClass() {
        return HuoshanEmbeddingProperty.class;
    }

    @Data
    static class HuoshanEmbeddingResponse extends OpenapiResponse {
        private String id;
        private Integer created;
        private String object;
        private Object data;
        private String model;
        private EmbeddingResponse.TokenUsage usage;
    }
}
