package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.protocol.IProtocolProperty;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DocParseProperty implements IProtocolProperty {
    private String[] supportTypes;

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("supportTypes", "支持的类型");
        return map;
    }
}
