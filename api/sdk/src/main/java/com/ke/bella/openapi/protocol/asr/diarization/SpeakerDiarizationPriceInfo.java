package com.ke.bella.openapi.protocol.asr.diarization;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Price information for speaker diarization requests
 * 基于音频时长计费（秒），说话人识别按音频实际时长收费
 */
@Data
public class SpeakerDiarizationPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 说话人识别单价（元/小时）
     */
    private BigDecimal price;
    private double batchDiscount = 1.0;

    @Override
    public String getUnit() {
        return "元/小时";
    }

    @Override
    public Map<String, String> description() {
        return ImmutableMap.of("price", "说话人识别每小时价格（元）");
    }
}
