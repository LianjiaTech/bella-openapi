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
    private double batchDiscount = 1.0;

    @Override
    public String getUnit() {
        return "元/张";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("details", "价格详情");
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

        @Override
        public String getUnit() {
            return "元/张";
        }

        @Override
        public Map<String, String> description() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("size", "图片尺寸");
            map.put("ldPricePerImage", "每张图片价格（低质量）");
            map.put("mdPricePerImage", "每张图片价格（中等质量）");
            map.put("hdPricePerImage", "每张图片价格（高清质量）");
            map.put("textTokenPrice", "文字token（/千token）");
            map.put("imageTokenPrice", "图片token（/千token）");
            return map;
        }

        @Override
        public String toString() {
            return "尺寸：" + size + "\n" + "低清：" + ldPricePerImage + "\n" + "中清：" + mdPricePerImage + "\n" + "高清：" + hdPricePerImage;
        }
    }
}
