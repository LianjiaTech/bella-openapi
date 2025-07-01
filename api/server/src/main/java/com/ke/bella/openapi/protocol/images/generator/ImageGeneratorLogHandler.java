package com.ke.bella.openapi.protocol.images.generator;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import org.springframework.stereotype.Component;

@Component
public class ImageGeneratorLogHandler implements EndpointLogHandler {
    @Override
    public void process(EndpointProcessData endpointProcessData) {
        OpenapiResponse openapiResponse = endpointProcessData.getResponse();
        if(openapiResponse instanceof ImagesResponse) {
            ImagesResponse response = (ImagesResponse) openapiResponse;
            String quality = getQualityFromResponse(response);
            String size = getSizeFromResponse(response);
            ImagesResponse.Usage usage = response.getUsage();
            if(response.getUsage() == null) {
                usage = new ImagesResponse.Usage();
            }
            usage.setNum(response.getData() != null ? response.getData().size() : 1);
            usage.setQuality(quality);
            usage.setSize(size);
            endpointProcessData.setUsage(usage);
        }
    }

    private String getQualityFromResponse(ImagesResponse response) {
        // 从响应的 ImageData 中获取质量信息
        if (response.getData() != null && !response.getData().isEmpty()) {
            String quality = response.getData().get(0).getQuality();
            return quality != null ? quality : "high";
        }
        return "high";
    }

    private String getSizeFromResponse(ImagesResponse response) {
        // 从响应的 ImageData 中获取尺寸信息
        if (response.getData() != null && !response.getData().isEmpty()) {
            String size = response.getData().get(0).getSize();
            return size != null ? size : "1024x1024";
        }
        return "1024x1024";
    }

    @Override
    public String endpoint() {
        return "/v1/images/generations";
    }
}
