package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ImagesEditsPriceInfo implements IPriceInfo, Serializable {
    private String billingMode;
    private BigDecimal pricePerEdit;
    private BigDecimal textTokenPrice;
    private BigDecimal imageTokenPrice;
    private BigDecimal imageInputTokenPrice;
    private BigDecimal imageOutputTokenPrice;
    private double batchDiscount = 1.0;
    private double supplierDiscount = 1.0;

    @Override
    public String getUnit() {
        return "分/张";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("billingMode", "计费模式：per_image（仅按张）、token（仅token）、mixed（按张+token）；为空保持历史逻辑");
        map.put("pricePerEdit", "单张图像价格（分/张）");
        map.put("textTokenPrice", "文字token价格（分/千token）");
        map.put("imageInputTokenPrice", "输入图片token价格（分/千token）");
        map.put("imageOutputTokenPrice", "输出图片token价格（分/千token）");
        map.put("imageTokenPrice", "图片token兜底价格（分/千token），兼容旧字段");
        return map;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "计费模式", billingMode);
        appendLine(builder, "单张图像价格", pricePerEdit);
        appendLine(builder, "文字token价格", textTokenPrice);
        appendLine(builder, "输入图片token价格", imageInputTokenPrice);
        appendLine(builder, "输出图片token价格", imageOutputTokenPrice);
        appendLine(builder, "图片token兜底价格", imageTokenPrice);
        return builder.length() == 0 ? "N/A" : builder.toString();
    }

    private static void appendLine(StringBuilder builder, String label, Object value) {
        if(value == null || (value instanceof String && ((String) value).isEmpty())) {
            return;
        }
        if(builder.length() > 0) {
            builder.append("\n");
        }
        builder.append(label).append("：").append(value);
    }
}
