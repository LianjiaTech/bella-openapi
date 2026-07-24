package com.ke.bella.openapi.protocol.tts;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class MiniMaxProperty extends TtsProperty {
    AuthorizationProperty auth;
    String deployName;
    String groupId;
}
