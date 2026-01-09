package com.ke.bella.openapi.protocol.web;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web Search Price Information Pricing details for Tavily web search services
 * Basic Search: 1 API credit per request Advanced Search: 2 API credits
 * per request
 */
@Data
public class WebSearchPriceInfo implements IPriceInfo, Serializable {

    /**
     * Price per basic search request (分/请求) Basic Search: Each request costs 1
     * API credit
     */
    private BigDecimal basicSearchPrice;

    /**
     * Price per advanced search request (分/请求) Advanced Search: Each request
     * costs 2 API credits
     */
    private BigDecimal advancedSearchPrice;

    private double batchDiscount = 1.0;

    @Override
    public String getUnit() {
        return "分/请求";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("basicSearchPrice", "基础搜索价格（分/请求）");
        map.put("advancedSearchPrice", "高级搜索价格（分/请求）");
        return map;
    }

    @Override
    public String toString() {
        return "基础搜索：" + basicSearchPrice + " 分/请求\n" +
                "高级搜索：" + advancedSearchPrice + " 分/请求";
    }
}
