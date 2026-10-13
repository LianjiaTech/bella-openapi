package com.ke.bella.openapi.protocol.live;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class LivePriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal inputTextTokenPrice;
    private BigDecimal inputAudioTokenPrice;
    private BigDecimal inputVideoTokenPrice;
    private BigDecimal outputTextTokenPrice;
    private BigDecimal outputAudioTokenPrice;
    private BigDecimal cachedTokenPrice;
    private BigDecimal sessionMinutePrice;
    private BigDecimal toolCallPrice;
    private double supplierDiscount = 1.0;

    @Override
    public String getUnit() {
        return "分（Token 单价按百万 Token，时长按分钟）";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> description = new LinkedHashMap<>();
        description.put("inputTextTokenPrice", "每百万输入文本 Token 价格");
        description.put("inputAudioTokenPrice", "每百万输入音频 Token 价格");
        description.put("inputVideoTokenPrice", "每百万输入视频 Token 价格");
        description.put("outputTextTokenPrice", "每百万输出文本 Token 价格");
        description.put("outputAudioTokenPrice", "每百万输出音频 Token 价格");
        description.put("cachedTokenPrice", "每百万缓存 Token 价格");
        description.put("sessionMinutePrice", "每分钟会话价格（分/分钟）");
        description.put("toolCallPrice", "每次工具调用价格（分/次）");
        description.put("supplierDiscount", "供应商折扣");
        return description;
    }
}
