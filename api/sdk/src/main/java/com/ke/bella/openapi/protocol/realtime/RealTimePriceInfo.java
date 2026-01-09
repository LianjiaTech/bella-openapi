package com.ke.bella.openapi.protocol.realtime;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.protocol.IPriceInfo;

import lombok.Data;

@Data
public class RealTimePriceInfo implements IPriceInfo, Serializable {
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
