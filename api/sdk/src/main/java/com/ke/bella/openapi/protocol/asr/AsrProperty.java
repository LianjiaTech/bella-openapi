package com.ke.bella.openapi.protocol.asr;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.Data;

@Data
public class AsrProperty {
    String deployName;
    AuthorizationProperty auth;
}
