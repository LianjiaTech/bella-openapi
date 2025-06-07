package com.ke.bella.openapi.protocol;

import com.google.common.collect.ImmutableSortedMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AuthorizationProperty implements IProtocolProperty {
    @Override
    public Map<String, String> description() {
        return ImmutableSortedMap.of("type", "鉴权方式", "header", "自定义的认证头",
                "apiKey", "apiKey(IAM验签时同ak)", "secret", "sk");
    }

    @Getter
    @AllArgsConstructor
    public enum AuthType {
        BASIC,
        BEARER,
        IAM,
        CUSTOM,
    }

    AuthType type;
    String header;
    String apiKey;
    String secret;
}
