package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 图片相关接口的基础日志处理器
 * 提供通用的日志处理逻辑，子类只需实现具体的endpoint方法
 */
public abstract class ImagesLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData endpointProcessData) {
        OpenapiResponse openapiResponse = endpointProcessData.getResponse();
        ImagesResponse response = null;
        if(openapiResponse instanceof ImagesResponse) {
            response = (ImagesResponse) openapiResponse;
        } else if(StringUtils.isNotBlank(endpointProcessData.getResponseRaw())) {
            response = JacksonUtils.deserialize(endpointProcessData.getResponseRaw(), ImagesResponse.class);
        }
        if(response != null) {
            ImagesResponse.Usage usage = response.getUsage();
            if(response.getUsage() == null) {
                usage = new ImagesResponse.Usage();
            }
            if(hasExplicitBillingMode(endpointProcessData.getPriceInfo())) {
                ImagesRequest request = resolveRequest(endpointProcessData);
                usage.setNum(resolveImageCount(response, usage, request));
                usage.setQuality(normalizeQuality(resolveQuality(response, usage, request)));
                usage.setSize(resolveSize(response, usage, request));
            } else {
                usage.setNum(response.getData() != null ? response.getData().size() : 1);
                usage.setQuality(getLegacyQualityFromResponse(response));
                usage.setSize(getLegacySizeFromResponse(response));
            }
            endpointProcessData.setUsage(usage);
        }
    }

    protected ImagesRequest resolveRequest(EndpointProcessData endpointProcessData) {
        if(endpointProcessData.getRequest() instanceof ImagesRequest) {
            return (ImagesRequest) endpointProcessData.getRequest();
        }
        if(StringUtils.isNotBlank(endpointProcessData.getRequestRaw())) {
            return JacksonUtils.deserialize(endpointProcessData.getRequestRaw(), ImagesRequest.class);
        }
        return null;
    }

    protected boolean hasExplicitBillingMode(String priceInfo) {
        if(StringUtils.isBlank(priceInfo)) {
            return false;
        }
        ImagesPriceInfo price = JacksonUtils.deserialize(priceInfo, ImagesPriceInfo.class);
        return price != null && StringUtils.isNotBlank(price.getBillingMode());
    }

    protected int resolveImageCount(ImagesResponse response, ImagesResponse.Usage usage, ImagesRequest request) {
        if(usage.getNum() != null) {
            return usage.getNum();
        }
        if(response.getData() != null) {
            return response.getData().size();
        }
        if(request != null && request.getN() != null) {
            return request.getN();
        }
        return 1;
    }

    protected String resolveQuality(ImagesResponse response, ImagesResponse.Usage usage, ImagesRequest request) {
        if(usage.getQuality() != null) {
            return usage.getQuality();
        }
        if(response.getData() != null && !response.getData().isEmpty()) {
            String quality = response.getData().get(0).getQuality();
            if(quality != null) {
                return quality;
            }
        }
        if(request != null && request.getQuality() != null) {
            return request.getQuality();
        }
        return "high";
    }

    protected String resolveSize(ImagesResponse response, ImagesResponse.Usage usage, ImagesRequest request) {
        if(usage.getSize() != null) {
            return usage.getSize();
        }
        if(response.getData() != null && !response.getData().isEmpty()) {
            String size = response.getData().get(0).getSize();
            if(size != null) {
                return size;
            }
        }
        if(request != null && request.getSize() != null) {
            return request.getSize();
        }
        return "1024x1024";
    }

    protected String normalizeQuality(String quality) {
        if(quality == null) {
            return "high";
        }
        switch (quality.toLowerCase()) {
        case "hd":
        case "high":
            return "high";
        case "standard":
        case "medium":
            return "medium";
        case "low":
            return "low";
        default:
            return quality;
        }
    }

    protected String getLegacyQualityFromResponse(ImagesResponse response) {
        if(response.getData() != null && !response.getData().isEmpty()) {
            String quality = response.getData().get(0).getQuality();
            return quality != null ? quality : "high";
        }
        return "high";
    }

    protected String getLegacySizeFromResponse(ImagesResponse response) {
        if(response.getData() != null && !response.getData().isEmpty()) {
            String size = response.getData().get(0).getSize();
            return size != null ? size : "1024x1024";
        }
        return "1024x1024";
    }
}
