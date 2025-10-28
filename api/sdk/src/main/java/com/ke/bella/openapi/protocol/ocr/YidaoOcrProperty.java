package com.ke.bella.openapi.protocol.ocr;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;

import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@EqualsAndHashCode(callSuper = true)
public class YidaoOcrProperty extends OcrProperty {

    AuthorizationProperty auth;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = super.description();
        map.put("auth", "鉴权配置");
        return map;
    }
}
