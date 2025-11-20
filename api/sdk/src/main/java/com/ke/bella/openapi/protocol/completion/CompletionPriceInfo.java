package com.ke.bella.openapi.protocol.completion;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CompletionPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal input;
    private BigDecimal output;
    private BigDecimal imageInput;
    private BigDecimal imageOutput;
    private BigDecimal cachedRead;
    private BigDecimal cachedCreation;
    private String unit = "分/千token";

    public BigDecimal getCachedCreation() {
        if(cachedCreation == null && cachedRead != null && input != null) {
            return BigDecimal.valueOf(input.doubleValue() * 1.25);
        }
        return cachedCreation;
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("input", "输入token单价（分/千token）");
        map.put("output", "输出token单价（分/千token）");
        map.put("cachedRead", "命中缓存token单价（分/千token）");
        map.put("cachedCreation", "创建缓存token单价（分/千token）");
        map.put("imageInput", "图片输入token单价（分/千token）");
        map.put("imageOutput", "图片输出token单价（分/千token）");
        return map;
    }
}
