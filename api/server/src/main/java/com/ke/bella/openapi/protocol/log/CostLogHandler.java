package com.ke.bella.openapi.protocol.log;

import com.google.common.collect.Maps;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.protocol.cost.CostCounter;
import com.ke.bella.openapi.protocol.cost.CostDetails;
import com.ke.bella.openapi.utils.GroovyExecutor;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.lmax.disruptor.EventHandler;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class CostLogHandler implements EventHandler<LogEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(CostLogHandler.class);

    public CostLogHandler(CostCounter costCounter, CostScripFetcher costScripFetcher) {
        this.costCounter = costCounter;
        this.costScripFetcher = costScripFetcher;
    }

    private final CostCounter costCounter;
    private final CostScripFetcher costScripFetcher;

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) throws Exception {
        EndpointProcessData log = event.getData();
        BigDecimal cost = BigDecimal.ZERO;
        CostDetails costDetails = null;
        if(log.isInnerLog()) {
            if(log.getPriceInfo() == null) {
                LOGGER.warn("price Info is null, channelCode:{}, requestId:{}", log.getChannelCode(), log.getRequestId());
                return;
            }
            if(log.getUsage() == null) {
                LOGGER.warn("usage is null, endpoint:{}, requestId:{}", log.getEndpoint(), log.getRequestId());
                return;
            }
            costDetails = CostCalculator.calculate(log.getEndpoint(), log.getPriceInfo(), log.getUsage());
            cost = costDetails.getTotalCost();
            if(log.isBatch()) {
                Map<String, Object> priceInfoMap = JacksonUtils.deserialize(log.getPriceInfo(), Map.class);
                double batchDiscount = MapUtils.getDoubleValue(priceInfoMap, "batchDiscount", 1.0);
                costDetails = applyDiscount(costDetails, BigDecimal.valueOf(batchDiscount));
                cost = costDetails.getTotalCost();
            }
        } else {
            String script = costScripFetcher.fetchCosetScript(log.getEndpoint());
            if(script != null) {
                try {
                    Map<String, Object> params = new HashMap<>();
                    Map<String, Object> price = JacksonUtils.toMap(log.getPriceInfo());
                    params.put("price", price == null ? Maps.newHashMap() : price);
                    params.put("usage", log.getUsage() == null ? Maps.newHashMap() : log.getUsage());
                    cost = BigDecimal.valueOf(Double.parseDouble(GroovyExecutor.executeScript(script, params).toString()));
                } catch (Exception e) {
                    LOGGER.warn("cost scrip run failed, endpoint: " + log.getEndpoint() + "; " + e.getMessage(), e);
                }
            }
        }
        boolean valid = cost != null && BigDecimal.ZERO.compareTo(cost) < 0;
        log.setCost(valid ? cost : BigDecimal.ZERO);
        log.setCostDetails(costDetails);
        if(log.isPrivate()) {
            return;
        }
        if(valid) {
            costCounter.delta(log.getAkCode(), cost);
            if(StringUtils.isNotEmpty(log.getParentAkCode())) {
                costCounter.delta(log.getParentAkCode(), cost);
            }
        }
    }

    @FunctionalInterface
    public interface CostScripFetcher {
        String fetchCosetScript(String endpoint);
    }

    private static CostDetails applyDiscount(CostDetails costDetails, BigDecimal discount) {
        if(costDetails == null) {
            return null;
        }

        return CostDetails.builder()
                .totalCost(costDetails.getTotalCost().multiply(discount))
                .inputDetails(applyDiscountToDetailItems(costDetails.getInputDetails(), discount))
                .outputDetails(applyDiscountToDetailItems(costDetails.getOutputDetails(), discount))
                .toolDetails(applyDiscountToToolItems(costDetails.getToolDetails(), discount))
                .build();
    }

    private static Map<String, CostDetails.CostDetailItem> applyDiscountToDetailItems(
            Map<String, CostDetails.CostDetailItem> items, BigDecimal discount) {
        if(items == null || items.isEmpty()) {
            return items;
        }

        return items.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> CostDetails.CostDetailItem.builder()
                                .tokens(entry.getValue().getTokens())
                                .unitPrice(entry.getValue().getUnitPrice())
                                .cost(entry.getValue().getCost().multiply(discount))
                                .build()
                ));
    }

    private static Map<String, CostDetails.ToolCostDetailItem> applyDiscountToToolItems(
            Map<String, CostDetails.ToolCostDetailItem> items, BigDecimal discount) {
        if(items == null || items.isEmpty()) {
            return items;
        }

        return items.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> CostDetails.ToolCostDetailItem.builder()
                                .callCount(entry.getValue().getCallCount())
                                .unitPrice(entry.getValue().getUnitPrice())
                                .cost(entry.getValue().getCost().multiply(discount))
                                .build()
                ));
    }
}
