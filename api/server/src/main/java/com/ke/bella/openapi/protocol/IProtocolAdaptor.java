package com.ke.bella.openapi.protocol;

import okhttp3.Request;

public interface IProtocolAdaptor {

    String endpoint();

    String getDescription();

    Class<?> getPropertyClass();

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
        case GOOGLE_JSON:
            // GOOGLE_JSON类型的鉴权由具体的适配器实现处理，这里提供默认的Bearer方式
            return builder.header("Authorization", "Bearer " + property.getApiKey());
        default:
            return builder.header("Authorization", "Bearer " + property.getApiKey());
        }
    }
}
