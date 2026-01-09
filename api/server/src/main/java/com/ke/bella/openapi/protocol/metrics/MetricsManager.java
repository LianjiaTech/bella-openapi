package com.ke.bella.openapi.protocol.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.MatchUtils;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MetricsManager {
    @Autowired
    private LuaScriptExecutor executor;
    public static final String unavailable_mark_key = "bella-openapi-channel-metrics:%s:unavailable";
    @Autowired
    private List<MetricsResolver> resolvers;
    @Autowired
    private RedissonClient redisson;

    public void record(EndpointProcessData processData) throws IOException {
        String endpoint = processData.getEndpoint();
        if(endpoint == null || processData.getChannelCode() == null) {
            return;
        }
        MetricsResolver resolver = resolvers.stream().filter(t -> MatchUtils.matchUrl(t.support(), endpoint))
                .findAny()
                .orElse(null);
        int unavailableSeconds = resolver == null ? 0 : resolver.resolveUnavailableSeconds(processData);
        Collection<String> metricsName;
        if(resolver != null) {
            metricsName = resolver.metricsName();
        } else {
            metricsName = processData.getMetrics().keySet();
        }
        List<Object> key = Lists.newArrayList(processData.getChannelCode());
        List<Object> metrics = new ArrayList<>();
        OpenapiResponse response = processData.getResponse();
        int minCompletedThreshold = 10;
        int errorRateThreshold = 30;// 百分比
        int httpCode = (response == null || response.getError() == null) ? 200 : response.getError().getHttpCode();
        metrics.add(minCompletedThreshold);
        metrics.add(errorRateThreshold);
        metrics.add(httpCode);
        metrics.add(unavailableSeconds);
        metrics.add(DateTimeUtils.getCurrentSeconds());
        metrics.add("errors");
        metrics.add(httpCode < 500 ? 0 : 1);
        metrics.add("request_too_many");
        metrics.add(httpCode == 429 ? 1 : 0);
        metrics.add("completed");
        metrics.add(1);
        if(processData.getMetrics() != null) {
            Map<String, Object> processDataMetrics = processData.getMetrics();
            metricsName.forEach(name -> {
                if(processDataMetrics.containsKey(name)) {
                    metrics.add(name);
                    metrics.add(processDataMetrics.get(name));
                }
            });
        }
        executor.execute(processData.getEndpoint(), ScriptType.metrics, key, metrics);
    }

    public Set<String> getAllUnavailableChannels(List<String> channelCodes) {
        Map<String, String> map = redisson.getBuckets().get(channelCodes.stream()
                .map(c -> String.format(unavailable_mark_key, c)).toArray(String[]::new));
        return map.keySet().stream().map(s -> s.split(":")[1]).collect(Collectors.toSet());
    }

    public Map<String, Map<String, Object>> queryMetrics(String endpoint, Collection<String> channelCodes) throws IOException {
        MetricsResolver resolver = resolvers.stream()
                .filter(t -> MatchUtils.matchUrl(t.support(), endpoint))
                .findAny()
                .orElse(null);

        List<String> metricsName = new ArrayList<>();
        if(resolver != null) {
            metricsName = resolver.metricsName();
        }

        List<Object> keys = Lists.newArrayList(channelCodes);
        List<Object> params = new ArrayList<>();
        params.add("completed");
        params.add("errors");
        params.add("request_too_many");
        params.addAll(metricsName);

        Object result = executor.execute(endpoint, ScriptType.metricsQuery, keys, params);
        return convertToQueryResult(result);
    }

    private Map<String, Map<String, Object>> convertToQueryResult(Object result) {
        if(result == null) {
            return Maps.newHashMap();
        }
        if(result instanceof String) {
            return JacksonUtils.deserialize((String) result, new TypeReference<Map<String, Map<String, Object>>>() {
            });
        }
        return Maps.newHashMap();
    }
}
