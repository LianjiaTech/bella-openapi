package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;
import java.util.Collections;
import java.util.Map;

@Data
public class DoubaoSearchProperty implements IProtocolProperty {
    private AuthorizationProperty auth;
    @Override public Map<String, String> description() { return Collections.singletonMap("auth", "鉴权配置"); }
}
