package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.ComponentList;
import com.ke.bella.openapi.protocol.IPriceInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ImagesPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;
    ImagesPriceInfoDetailsList details;
    /**
     * Explicit billing mode for image generation.
     * Blank keeps the legacy behavior: token input details plus per-image price.
     */
    private String billingMode;
    private double batchDiscount = 1.0;
    private double supplierDiscount = 1.0;

    @Override
    public String getUnit() {
        return "分/张";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("details", "价格详情（按张价格单位：分/张，token价格单位：分/千token）");
        map.put("billingMode", "计费模式：per_image（仅按张）、token（仅token）、mixed（按张+token）；为空保持历史逻辑");
        return map;
    }

    public static class ImagesPriceInfoDetailsList extends ArrayList<ImagesPriceInfoDetails> implements ComponentList<ImagesPriceInfoDetails> {
        private static final long serialVersionUID = 1L;

        @Override
        public String toString() {
            if(size() == 0) {
                return "N/A";
            }
            ImagesPriceInfoDetails details = get(0);
            for (ImagesPriceInfoDetails d : this) {
                if("1024x1024".equals(d.getSize())) {
                    details = d;
                }
            }
            return details.toString();
        }

        @Override
        public Class<ImagesPriceInfoDetails> getComponentType() {
            return ImagesPriceInfoDetails.class;
        }
    }

    @Data
    public static class ImagesPriceInfoDetails implements IPriceInfo, Serializable {
        private static final long serialVersionUID = 1L;
        private String size;
        private BigDecimal ldPricePerImage;
        private BigDecimal mdPricePerImage;
        private BigDecimal hdPricePerImage;
        private BigDecimal textTokenPrice;
        private BigDecimal imageTokenPrice;
        private BigDecimal imageInputTokenPrice;
        private BigDecimal imageOutputTokenPrice;

        @Override
        public String getUnit() {
            return "分/张";
        }

        @Override
        public Map<String, String> description() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("size", "图片尺寸");
            map.put("ldPricePerImage", "每张图片价格（低质量，分/张）");
            map.put("mdPricePerImage", "每张图片价格（中等质量，分/张）");
            map.put("hdPricePerImage", "每张图片价格（高清质量，分/张）");
            map.put("textTokenPrice", "文字token价格（分/千token）");
            map.put("imageInputTokenPrice", "输入图片token价格（分/千token）");
            map.put("imageOutputTokenPrice", "输出图片token价格（分/千token）");
            map.put("imageTokenPrice", "图片token兜底价格（分/千token），兼容旧字段");
            return map;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            appendLine(builder, "尺寸", size);
            appendLine(builder, "低清", ldPricePerImage);
            appendLine(builder, "中清", mdPricePerImage);
            appendLine(builder, "高清", hdPricePerImage);
            appendLine(builder, "文字token价格", textTokenPrice);
            appendLine(builder, "输入图片token价格", imageInputTokenPrice);
            appendLine(builder, "输出图片token价格", imageOutputTokenPrice);
            appendLine(builder, "图片token兜底价格", imageTokenPrice);
            return builder.length() == 0 ? "N/A" : builder.toString();
        }

        private static void appendLine(StringBuilder builder, String label, Object value) {
            if(value == null) {
                return;
            }
            if(value instanceof String && ((String) value).isEmpty()) {
                return;
            }
            if(builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(label).append("：").append(value);
        }
    }
}
