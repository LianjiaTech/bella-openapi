package com.ke.bella.openapi.protocol.cost;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CostDetails implements Serializable {
    private static final long serialVersionUID = 1L;

    private BigDecimal totalCost;

    private Map<String, CostDetailItem> inputDetails;

    private Map<String, CostDetailItem> outputDetails;

    private Map<String, ToolCostDetailItem> toolDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CostDetailItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer tokens;

        private BigDecimal unitPrice;

        private BigDecimal cost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCostDetailItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer callCount;

        private BigDecimal unitPrice;

        private BigDecimal cost;
    }
}
