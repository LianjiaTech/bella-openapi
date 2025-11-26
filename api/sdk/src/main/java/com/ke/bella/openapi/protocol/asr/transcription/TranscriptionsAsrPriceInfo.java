package com.ke.bella.openapi.protocol.asr.transcription;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
public class TranscriptionsAsrPriceInfo implements IPriceInfo, Serializable {
    private BigDecimal price;
    private double batchDiscount = 1.0;

    @Override
    public String getUnit() {
        return "时/元";
    }

    @Override
    public Map<String, String> description() {
        return ImmutableMap.of("price", "每小时价格（元）");
    }
}
