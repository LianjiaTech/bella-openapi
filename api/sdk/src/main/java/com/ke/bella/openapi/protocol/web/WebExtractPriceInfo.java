package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web Extract Price Information
 * Pricing details for Tavily web extract services
 * Basic Extract: 1 API credit per 5 successful URL extractions
 * Advanced Extract: 2 API credits per 5 successful URL extractions
 */
@Data
public class WebExtractPriceInfo implements IPriceInfo, Serializable {

    /**
     * Price per basic extraction (分/次提取)
     * Basic Extract: Every successful URL extractions cost 1 API credit
     */
    private BigDecimal basicExtractionPrice;

    /**
     * Price per advanced extraction (分/次提取)
     * Advanced Extract: Every successful URL extractions cost 2 API credits
     */
    private BigDecimal advancedExtractionPrice;

    private double batchDiscount = 1.0;

    @Override
    public String getUnit() {
        return "分/请求";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("basicExtractionPrice", "基础提取价格（分/次提取）");
        map.put("advancedExtractionPrice", "高级提取价格（分/次提取）");
        return map;
    }

    @Override
    public String toString() {
        return "基础提取：" + basicExtractionPrice + " 分/5次提取\n" +
               "高级提取：" + advancedExtractionPrice + " 分/5次提取";
    }
}
