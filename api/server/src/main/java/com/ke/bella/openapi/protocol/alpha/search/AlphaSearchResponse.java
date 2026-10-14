package com.ke.bella.openapi.protocol.alpha.search;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Getter
@AllArgsConstructor
public final class AlphaSearchResponse {
    private final int status;
    private final String contentType;
    @Getter(AccessLevel.NONE)
    private final byte[] body;
    private final AlphaSearchUsage usage;

    public byte[] getBody() {
        return Arrays.copyOf(body, body.length);
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
