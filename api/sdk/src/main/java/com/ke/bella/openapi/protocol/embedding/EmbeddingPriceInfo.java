package com.ke.bella.openapi.protocol.embedding;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class EmbeddingPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal input;
    private String unit = "分/千token";
    private double batchDiscount = 1.0;

    @Override
    public Map<String, String> description() {
        return ImmutableMap.of("input", "输入token单价（分/千token）");
    }
}
