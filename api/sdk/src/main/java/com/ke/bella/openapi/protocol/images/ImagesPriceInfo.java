package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Data
public class ImagesPriceInfo implements IPriceInfo {
    private BigDecimal pricePerImage = BigDecimal.ZERO;
    private BigDecimal hdPricePerImage = BigDecimal.ZERO;
    private String unit = "张";

    @Override
    public String getUnit() {
        return unit;
    }

    @Override
    public Map<String, String> description() {
        SortedMap<String, String> map = new TreeMap<>();
        map.put("pricePerImage", "每张图片价格（标准质量）");
        map.put("hdPricePerImage", "每张图片价格（高清质量）");
        return map;
    }
}
