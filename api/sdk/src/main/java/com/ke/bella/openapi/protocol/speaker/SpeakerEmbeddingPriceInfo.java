package com.ke.bella.openapi.protocol.speaker;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Price information for speaker embedding requests
 * 基于音频时长计费
 */
@Data
public class SpeakerEmbeddingPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 音频处理单价（元/小时）
     */
    private BigDecimal price;
    private double batchDiscount = 1.0;

    @Override
    public String getUnit() {
        return "元/小时";
    }

    @Override
    public Map<String, String> description() {
        return ImmutableMap.of("price", "每小时价格（元）");
    }
}
