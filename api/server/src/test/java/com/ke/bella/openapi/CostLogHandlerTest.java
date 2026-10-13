package com.ke.bella.openapi;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.util.concurrent.AtomicDouble;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.completion.CompletionLogHandler;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.cost.CostCounter;
import com.ke.bella.openapi.protocol.log.CostLogHandler;
import com.ke.bella.openapi.protocol.log.LogEvent;

public class CostLogHandlerTest {

    @Test
    public void test0() throws Exception {
        final AtomicDouble result = new AtomicDouble(0);
        CostCounter.CostRecorder recorder = (apikey, month, cost) -> result.getAndAdd(cost.doubleValue());
        CostCounter costCounter = new CostCounter(recorder);
        CostLogHandler.CostScripFetcher fetcher = endpoint -> "price.price * usage";
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, fetcher);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("xxxx");
        processData.setAkCode(UUID.randomUUID().toString());
        processData.setUsage(100);
        processData.setPriceInfo("{\"price\": 10.000}");
        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1 ,true);
        costCounter.flush();
        Assertions.assertEquals(result.doubleValue(), processData.getCost().doubleValue());
        Assertions.assertEquals(result.doubleValue(), 1000);
    }

    @Test
    public void test1() throws Exception {
        final AtomicDouble result = new AtomicDouble(0);
        CostCounter.CostRecorder recorder = (apikey, month, cost) -> result.getAndAdd(cost.doubleValue());
        CostCounter costCounter = new CostCounter(recorder);
        CostLogHandler.CostScripFetcher fetcher = endpoint -> "price.input * usage";
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, fetcher);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("xxxx");
        processData.setAkCode(UUID.randomUUID().toString());
        processData.setUsage(JacksonUtils.toMap("{\"input_tokens\": 100}"));
        processData.setPriceInfo("{\"input\": 10.000}");
        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1 ,true);
        costCounter.flush();
        Assertions.assertEquals(result.doubleValue(), processData.getCost().doubleValue());
        Assertions.assertEquals(result.doubleValue(), 0);
    }

    @Test
    public void test2() throws Exception {
        final AtomicDouble result = new AtomicDouble(0);
        CostCounter.CostRecorder recorder = (apikey, month, cost) -> result.getAndAdd(cost.doubleValue());
        CostCounter costCounter = new CostCounter(recorder);
        CostLogHandler.CostScripFetcher fetcher = endpoint -> "price.input * usage.input_tokens";
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, fetcher);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("xxxx");
        processData.setAkCode(UUID.randomUUID().toString());
        processData.setUsage(JacksonUtils.toMap("{\"input_tokens\": 100}"));
        processData.setPriceInfo("{\"price\": 10.000}");
        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1 ,true);
        costCounter.flush();
        Assertions.assertEquals(result.doubleValue(), processData.getCost().doubleValue());
        Assertions.assertEquals(result.doubleValue(), 0);
    }

    @Test
    public void test3() throws Exception {
        final AtomicDouble result = new AtomicDouble(0);
        CostCounter.CostRecorder recorder = (apikey, month, cost) -> result.getAndAdd(cost.doubleValue());
        CostCounter costCounter = new CostCounter(recorder);
        CostLogHandler.CostScripFetcher fetcher = endpoint -> "price.input * usage.input_tokens";
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, fetcher);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("xxxx");
        processData.setAkCode(UUID.randomUUID().toString());
        processData.setUsage(JacksonUtils.toMap("{\"input_tokens\": 100}"));
        processData.setPriceInfo("{\"input\": 10.000}");
        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1 ,true);
        costCounter.flush();
        Assertions.assertEquals(result.doubleValue(), processData.getCost().doubleValue());
        Assertions.assertEquals(result.doubleValue(), 1000);
    }

    @Test
    public void test4() throws Exception {
        final AtomicDouble result = new AtomicDouble(0);
        CostCounter.CostRecorder recorder = (apikey, month, cost) -> result.getAndAdd(cost.doubleValue());
        CostCounter costCounter = new CostCounter(recorder);
        CostLogHandler.CostScripFetcher fetcher = endpoint -> null;
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, fetcher);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setEndpoint("xxxx");
        processData.setAkCode(UUID.randomUUID().toString());
        processData.setUsage(JacksonUtils.toMap("{\"input_tokens\": 100}"));
        processData.setPriceInfo("{\"input\": 10.000}");
        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1 ,true);
        costCounter.flush();
        Assertions.assertEquals(result.doubleValue(), processData.getCost().doubleValue());
        Assertions.assertEquals(result.doubleValue(), 0);
    }

    @Test
    public void shouldSkipInnerCostForUnroutedRequest() throws Exception {
        final AtomicDouble result = new AtomicDouble(0);
        AtomicInteger recordCount = new AtomicInteger(0);
        CostCounter.CostRecorder recorder = (apikey, month, cost) -> {
            recordCount.incrementAndGet();
            result.getAndAdd(cost.doubleValue());
        };
        CostCounter costCounter = new CostCounter(recorder);
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, endpoint -> null);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setInnerLog(true);
        processData.setFailureStage(EndpointProcessData.FailureStage.ADMISSION);
        processData.setCostExpectation(EndpointProcessData.CostExpectation.NOT_APPLICABLE);
        processData.setEndpoint("/v1/chat/completions");
        processData.setAkCode(UUID.randomUUID().toString());

        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1, true);
        costCounter.flush();

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(processData.getCost()));
        Assertions.assertNull(processData.getCostDetails());
        Assertions.assertEquals(0, recordCount.get());
        Assertions.assertEquals(0, result.doubleValue());
    }

    @Test
    public void shouldSkipInnerCostWhenDirectChannelLookupFails() throws Exception {
        AtomicInteger recordCount = new AtomicInteger(0);
        CostCounter costCounter = new CostCounter((apikey, month, cost) -> recordCount.incrementAndGet());
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, endpoint -> null);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setInnerLog(true);
        processData.setFailureStage(EndpointProcessData.FailureStage.CHANNEL_LOOKUP);
        processData.setCostExpectation(EndpointProcessData.CostExpectation.NOT_APPLICABLE);

        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1, true);
        costCounter.flush();

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(processData.getCost()));
        Assertions.assertNull(processData.getCostDetails());
        Assertions.assertEquals(0, recordCount.get());
    }

    @Test
    public void shouldSkipBeforeChannelCostWhenEndpointHandlerSynthesizesUsage() throws Exception {
        AtomicInteger recordCount = new AtomicInteger(0);
        CostCounter costCounter = new CostCounter((apikey, month, cost) -> recordCount.incrementAndGet());
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, endpoint -> null);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setInnerLog(true);
        processData.setFailureStage(EndpointProcessData.FailureStage.ROUTING);
        processData.setCostExpectation(EndpointProcessData.CostExpectation.NOT_APPLICABLE);
        processData.setEndpoint("/v1/chat/completions");
        processData.setResponse(OpenapiResponse.errorResponse(new OpenapiResponse.OpenapiError("rate_limit", "limited", 429)));

        new CompletionLogHandler().process(processData);
        Assertions.assertTrue(processData.getUsage() instanceof CompletionResponse.TokenUsage);

        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1, true);
        costCounter.flush();

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(processData.getCost()));
        Assertions.assertNull(processData.getCostDetails());
        Assertions.assertEquals(0, recordCount.get());
    }

    @Test
    public void shouldCalculateInnerCostWhenExpectationIsUnset() throws Exception {
        final AtomicDouble result = new AtomicDouble(0);
        AtomicInteger recordCount = new AtomicInteger(0);
        CostCounter.CostRecorder recorder = (apikey, month, cost) -> {
            recordCount.incrementAndGet();
            result.getAndAdd(cost.doubleValue());
        };
        CostCounter costCounter = new CostCounter(recorder);
        CostLogHandler costLogHandler = new CostLogHandler(costCounter, endpoint -> null);
        EndpointProcessData processData = new EndpointProcessData();
        processData.setInnerLog(true);
        processData.setFailureStage(EndpointProcessData.FailureStage.CHANNEL_LOOKUP);
        processData.setEndpoint("/v1/chat/completions");
        processData.setAkCode(UUID.randomUUID().toString());
        processData.setPriceInfo("{\"tiers\":[{\"inputRangePrice\":{\"minToken\":0,\"maxToken\":2147483647,\"input\":10,\"output\":20}}]}");
        processData.setUsage(JacksonUtils.toMap("{\"prompt_tokens\":5000,\"completion_tokens\":3000}"));

        LogEvent logEvent = new LogEvent();
        logEvent.setData(processData);
        costLogHandler.onEvent(logEvent, 1, true);
        costCounter.flush();

        Assertions.assertEquals(0, new BigDecimal("110").compareTo(processData.getCost()));
        Assertions.assertEquals(1, recordCount.get());
        Assertions.assertEquals(110, result.doubleValue());
    }
}
