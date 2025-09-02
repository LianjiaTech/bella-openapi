package com.ke.bella.openapi.protocol.log;

import com.google.common.collect.Maps;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.protocol.cost.CostCounter;
import com.ke.bella.openapi.utils.GroovyExecutor;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.lmax.disruptor.EventHandler;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

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
        if(log.isInnerLog()) {
            if(log.getPriceInfo() == null) {
                LOGGER.warn("price Info is null, channelCode:{}, requestId:{}", log.getChannelCode(), log.getRequestId());
                return;
            }
            if(log.getUsage() == null) {
                LOGGER.warn("usage is null, endpoint:{}, requestId:{}", log.getEndpoint(), log.getRequestId());
                return;
            }
            cost = CostCalculator.calculate(log.getEndpoint(), log.getPriceInfo(), log.getUsage());
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

}
