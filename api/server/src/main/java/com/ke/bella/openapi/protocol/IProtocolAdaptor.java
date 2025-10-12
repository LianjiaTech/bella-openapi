package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.ISummary;
import okhttp3.Request;
import java.util.Arrays;

public interface IProtocolAdaptor {

    String endpoint();

    String getDescription();

    Class<?> getPropertyClass();

    default Request.Builder authorizationRequestBuilder(AuthorizationProperty property) {
        Request.Builder builder = new Request.Builder();
        if(property == null) {
            return builder;
        }
        switch (property.type) {
        case BASIC:
            return builder.header("Authorization", property.getApiKey());
        case CUSTOM:
            return builder.header(property.getHeader(), property.getApiKey());
        default:
            return builder.header("Authorization", "Bearer " + property.getApiKey());
        }
    }

    /**
     * 批量清理可清理的对象，用于在HTTP请求期间释放内存
     *
     * @param clearables 需要清理的对象数组
     */
    default void clearLargeData(IMemoryClearable... clearables) {
        if(clearables != null) {
            Arrays.stream(clearables)
                    .filter(clearable -> clearable != null && !clearable.isCleared())
                    .filter(clearable -> EndpointContext.isLargeRequest()
                            || clearable instanceof ISummary
                            || clearable instanceof ITransfer)
                    .forEach(IMemoryClearable::clearLargeData);
        }
    }
}
