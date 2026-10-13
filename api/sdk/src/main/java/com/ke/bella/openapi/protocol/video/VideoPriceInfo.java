package com.ke.bella.openapi.protocol.video;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.ComponentList;
import com.ke.bella.openapi.protocol.IPriceInfo;

import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoPriceInfo implements IPriceInfo, Serializable {
    private static final long serialVersionUID = 1L;

    private String billingMode;

    private BigDecimal input;

    private BigDecimal output;

    private BigDecimal textOutput;

    private BigDecimal videoOutput;

    private DurationPriceDetailsList details = new DurationPriceDetailsList();

    private double supplierDiscount = 1.0;

    private BigDecimal inputImagePricePerImage;

    private BigDecimal inputAudioPricePerSecond;

    private Integer freeInputImageCount;

    public boolean isDurationBillingMode() {
        return "duration".equalsIgnoreCase(billingMode);
    }

    public boolean isMiniMaxBillingMode() {
        return "minimax".equalsIgnoreCase(billingMode);
    }

    public boolean isGeminiBillingMode() {
        return "gemini".equalsIgnoreCase(billingMode);
    }

    public boolean hasValidDurationPrice() {
        return CollectionUtils.isNotEmpty(details) && details.stream().allMatch(detail -> detail.getPricePerSecond() != null);
    }

    public BigDecimal matchPricePerSecond(VideoUsage usage) {
        if(CollectionUtils.isEmpty(details)) {
            return BigDecimal.ZERO;
        }
        return details.stream()
                .filter(detail -> resolutionEquals(detail.getResolution(), usage.getSr()))
                .filter(detail -> detail.getAudio() == null || detail.getAudio().equals(usage.getAudio()))
                .findFirst()
                .map(DurationPriceDetail::getPricePerSecond)
                .orElse(BigDecimal.ZERO);
    }

    private boolean resolutionEquals(String configured, String actual) {
        if(StringUtils.equals(configured, actual)) {
            return true;
        }
        return StringUtils.equals(normalizePResolution(configured), normalizePResolution(actual));
    }

    private String normalizePResolution(String resolution) {
        if(StringUtils.isBlank(resolution)) {
            return "";
        }
        String value = resolution.trim().toUpperCase();
        if(value.matches("\\d+P?")) {
            return value.replaceAll("P$", "");
        }
        return value;
    }

    @Override
    public String getUnit() {
        return "分/千token 或 分/秒";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("billingMode", "计费模式：token（默认）、duration、minimax 或 gemini");
        map.put("input", "输入token单价（分/千token）");
        map.put("output", "输出token单价（分/千token）");
        map.put("textOutput", "文本输出token单价（分/千token）");
        map.put("videoOutput", "视频输出token单价（分/千token）");
        map.put("details", "duration/minimax 模式明细，按 resolution/audio 匹配视频单价，pricePerSecond 为分/秒");
        map.put("inputImagePricePerImage", "输入图片素材单价（分/张）");
        map.put("inputAudioPricePerSecond", "输入音频素材单价（分/秒）");
        map.put("freeInputImageCount", "免费输入图片数量");
        return map;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DurationPriceDetailsList extends ArrayList<DurationPriceDetail> implements ComponentList<DurationPriceDetail> {
        private static final long serialVersionUID = 1L;

        @Override
        public Class<DurationPriceDetail> getComponentType() {
            return DurationPriceDetail.class;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DurationPriceDetail implements IPriceInfo, Serializable {
        private static final long serialVersionUID = 1L;

        private String resolution;

        private Boolean audio;

        private BigDecimal pricePerSecond;

        @Override
        public String getUnit() {
            return "分/秒";
        }

        @Override
        public Map<String, String> description() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("resolution", "分辨率档位");
            map.put("audio", "是否有声视频");
            map.put("pricePerSecond", "视频单价（分/秒）");
            return map;
        }
    }
}
