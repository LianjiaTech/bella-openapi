package com.ke.bella.openapi.protocol.asr.flash;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

@Data
public class FlashAsrPriceInfo implements IPriceInfo, Serializable  {

    private BigDecimal price;
    private double batchDiscount = 1.0;

    @Override
    public String getUnit() {
        return "分/次";
    }

    @Override
    public Map<String, String> description() {
        return ImmutableMap.of("price", "分/次");
    }
}
