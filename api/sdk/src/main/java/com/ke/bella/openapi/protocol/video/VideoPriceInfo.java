package com.ke.bella.openapi.protocol.video;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.IPriceInfo;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal input;

    private BigDecimal output;

    @Override
    public String getUnit() {
        return "分/千token";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("input", "输入token单价（分/千token）");
        map.put("output", "输出token单价（分/千token）");
        return map;
    }
}
