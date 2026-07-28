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

    private DurationPriceDetailsList details = new DurationPriceDetailsList();

    private double supplierDiscount = 1.0;

    public boolean isDurationBillingMode() {
        return "duration".equalsIgnoreCase(billingMode);
    }

    public boolean hasValidDurationPrice() {
        return CollectionUtils.isNotEmpty(details) && details.stream().allMatch(detail -> detail.getPricePerSecond() != null);
    }

    public BigDecimal matchPricePerSecond(VideoUsage usage) {
        if(CollectionUtils.isEmpty(details)) {
            return BigDecimal.ZERO;
        }
        return details.stream()
                .filter(detail -> StringUtils.equals(detail.getResolution(), usage.getSr()))
                .filter(detail -> detail.getAudio() == null || detail.getAudio().equals(usage.getAudio()))
                .findFirst()
                .map(DurationPriceDetail::getPricePerSecond)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public String getUnit() {
        return "分/千token 或 分/秒";
    }

    @Override
    public Map<String, String> description() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("billingMode", "计费模式：token（默认）或 duration");
        map.put("input", "输入token单价（分/千token）");
        map.put("output", "输出token单价（分/千token）");
        map.put("details", "duration 模式明细，按 resolution/audio 匹配输出单价，pricePerSecond 为分/秒");
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
            map.put("pricePerSecond", "输出单价（分/秒）");
            return map;
        }
    }
}
