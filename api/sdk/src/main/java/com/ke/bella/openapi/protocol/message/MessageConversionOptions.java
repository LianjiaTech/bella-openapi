package com.ke.bella.openapi.protocol.message;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessageConversionOptions {
    boolean nativeSupport;
    boolean supportOutputConfigEffort;
    boolean defaultThinkingEnabled;
    String defaultThinkingEffort;
}
