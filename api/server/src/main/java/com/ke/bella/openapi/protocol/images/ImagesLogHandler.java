package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;

/**
 * 图片相关接口的基础日志处理器
 * 提供通用的日志处理逻辑，子类只需实现具体的endpoint方法
 */
public abstract class ImagesLogHandler implements EndpointLogHandler {
    
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

    /**
     * 从响应中获取质量信息
     * @param response 图片响应对象
     * @return 质量信息，默认为"high"
     */
    protected String getQualityFromResponse(ImagesResponse response) {
        // 从响应的 ImageData 中获取质量信息
        if (response.getData() != null && !response.getData().isEmpty()) {
            String quality = response.getData().get(0).getQuality();
            return quality != null ? quality : "high";
        }
        return "high";
    }

    /**
     * 从响应中获取尺寸信息
     * @param response 图片响应对象
     * @return 尺寸信息，默认为"1024x1024"
     */
    protected String getSizeFromResponse(ImagesResponse response) {
        // 从响应的 ImageData 中获取尺寸信息
        if (response.getData() != null && !response.getData().isEmpty()) {
            String size = response.getData().get(0).getSize();
            return size != null ? size : "1024x1024";
        }
        return "1024x1024";
    }
}
