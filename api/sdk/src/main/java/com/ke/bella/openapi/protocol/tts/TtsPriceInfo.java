package com.ke.bella.openapi.protocol.tts;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class TtsPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal input;
    private String unit = "分/万字";
    private double batchDiscount = 1.0;

    @Override
    public Map<String, String> description() {
        return ImmutableMap.of("input", "输入字符单价（分/万字）");
    }
}
