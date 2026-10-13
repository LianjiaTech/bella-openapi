package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.protocol.metrics.MetricsQueryResult;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@BellaAPI
@RestController
@RequestMapping("/v1/metrics")
@Tag(name = "metrics查询")
public class MetricsController {
    @Autowired
    private MetricsManager metricsManager;
    @Autowired
    private ChannelService channelService;
    @Autowired
    private ModelService modelService;

    @GetMapping
    public List<MetricsQueryResult> query(@RequestParam String endpoint,
            @RequestParam(required = false, defaultValue = EntityConstants.MODEL) String channelType,
            @RequestParam(required = false) String model) throws IOException {
        Condition.ChannelCondition condition = new Condition.ChannelCondition();
        condition.setStatus(EntityConstants.ACTIVE);
        condition.setEntityType(channelType);
        if(channelType.equals(EntityConstants.ENDPOINT)) {
            condition.setEntityCode(endpoint);
        } else {
            if(StringUtils.isNotEmpty(model)) {
                condition.setEntityCode(modelService.fetchTerminalModelName(model));
            } else {
                Condition.ModelCondition modelCondition = new Condition.ModelCondition();
                modelCondition.setStatus(EntityConstants.ACTIVE);
                modelCondition.setEndpoint(endpoint);
                Set<String> models = modelService.listByCondition(modelCondition).stream().map(ModelDB::getModelName).collect(Collectors.toSet());
                condition.setEntityCodes(models);
            }
        }
        Map<String, String> channelMap = channelService.listByCondition(condition).stream()
                .collect(Collectors.toMap(ChannelDB::getChannelCode, ChannelDB::getEntityCode));
        Map<String, Map<String, Object>> metrics = metricsManager.queryMetrics(endpoint, channelMap.keySet());
        List<MetricsQueryResult> results = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : metrics.entrySet()) {
            MetricsQueryResult result = new MetricsQueryResult();
            result.setChannelCode(entry.getKey());
            result.setEndpoint(endpoint);
            result.setEntityCode(channelMap.get(entry.getKey()));
            result.setMetrics(entry.getValue());
            results.add(result);
        }
        return results;
    }

}
