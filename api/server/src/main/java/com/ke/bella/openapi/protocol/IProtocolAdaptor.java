package com.ke.bella.openapi.protocol;

import okhttp3.Request;

public interface IProtocolAdaptor {

    String endpoint();

    String getDescription();

    Class<?> getPropertyClass();

    /**
     * 验证渠道配置信息，在渠道创建时调用
     * @param channelInfo 渠道配置JSON字符串
     * @throws IllegalArgumentException 当配置无效时抛出异常
     */
    default void validateChannelInfo(String channelInfo) {
        // 默认不做验证，由各适配器根据需要重写
    }

    default Request.Builder authorizationRequestBuilder(AuthorizationProperty property) {
        Request.Builder builder = new Request.Builder();
        if(property == null) {
            return builder;
        }
        switch (property.getType()) {
        case BASIC:
            return builder.header("Authorization", property.getApiKey());
        case CUSTOM:
            return builder.header(property.getHeader(), property.getApiKey());
        default:
            return builder.header("Authorization", "Bearer " + property.getApiKey());
        }
    }
}
