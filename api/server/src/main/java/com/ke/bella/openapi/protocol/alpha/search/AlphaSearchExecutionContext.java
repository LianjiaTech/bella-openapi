package com.ke.bella.openapi.protocol.alpha.search;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class AlphaSearchExecutionContext {
    private final String forwardUrl;
    private final String deployName;
    private final Object property;

    public <T> T getProperty(Class<T> type) {
        return type.cast(property);
    }
}
