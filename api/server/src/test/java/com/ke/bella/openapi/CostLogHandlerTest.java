package com.ke.bella.openapi;

import java.util.UUID;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.util.concurrent.AtomicDouble;
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
}
