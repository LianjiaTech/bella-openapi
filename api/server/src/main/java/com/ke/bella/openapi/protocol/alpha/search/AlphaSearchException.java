package com.ke.bella.openapi.protocol.alpha.search;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import lombok.Getter;

@Getter
public class AlphaSearchException extends BellaException.ChannelException {
    private final String failureStage;

    public AlphaSearchException(Integer httpCode, String type, String message,
            OpenapiResponse.OpenapiError error, String failureStage) {
        super(httpCode, type, message, error);
        this.failureStage = failureStage;
    }

    public static AlphaSearchException provider(Integer httpCode, String type, String message,
            OpenapiResponse.OpenapiError error) {
        return new AlphaSearchException(httpCode, type, message, error,
                EndpointProcessData.FailureStage.PROVIDER);
    }

    public static AlphaSearchException forwarding(String message) {
        return new AlphaSearchException(502, "Channel Exception", message, null,
                EndpointProcessData.FailureStage.FORWARDING);
    }
}
