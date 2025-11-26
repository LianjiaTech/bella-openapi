package com.ke.bella.openapi.protocol.ocr;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableSortedMap;
import com.ke.bella.openapi.protocol.IPriceInfo;

import lombok.Data;

/**
 * OCR计费信息模型
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OcrPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal pricePerRequest;     // 每次请求价格
    private String unit = "元/次";           // 计费单位
    private double batchDiscount = 1.0;

    @Override
    public Map<String, String> description() {
        return ImmutableSortedMap.of("pricePerRequest", "请求价格");
    }
}
