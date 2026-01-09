package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web Crawl Price Information
 * Pricing details for Tavily web crawl services
 * Crawl Cost = Mapping Cost + Extraction Cost
 */
@Data
public class WebCrawlPriceInfo implements IPriceInfo, Serializable {

    /**
     * Price per basic mapping (per 10 pages)
     */
    private BigDecimal basicMappingPrice;

    /**
     * Price per mapping with instructions (per 10 pages)
     */
    private BigDecimal instructionMappingPrice;

    /**
     * Price per basic extraction (per 5 extractions)
     */
    private BigDecimal basicExtractionPrice;

    /**
     * Price per advanced extraction (per 5 extractions)
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
        map.put("basicMappingPrice", "基础映射价格（分/页）");
        map.put("instructionMappingPrice", "指令映射价格（分/页）");
        map.put("basicExtractionPrice", "基础提取价格（分/次提取）");
        map.put("advancedExtractionPrice", "高级提取价格（分/次提取）");
        return map;
    }

    @Override
    public String toString() {
        return "基础映射：" + basicMappingPrice + " 分/10页\n" +
                "指令映射：" + instructionMappingPrice + " 分/10页\n" +
                "基础提取：" + basicExtractionPrice + " 分/5次\n" +
                "高级提取：" + advancedExtractionPrice + " 分/5次";
    }
}
