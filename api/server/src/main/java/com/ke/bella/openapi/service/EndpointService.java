package com.ke.bella.openapi.service;

import static com.ke.bella.openapi.common.EntityConstants.ACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.ENDPOINT;
import static com.ke.bella.openapi.common.EntityConstants.INACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.INNER;
import static com.ke.bella.openapi.common.EntityConstants.MAINLAND;
import static com.ke.bella.openapi.common.EntityConstants.OVERSEAS;
import static com.ke.bella.openapi.common.EntityConstants.PROTECTED;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.alicp.jetcache.template.QuickConfig;
import com.google.common.collect.Lists;
import com.ke.bella.openapi.EnumDto;
import com.ke.bella.openapi.JsonSchema;
import com.ke.bella.openapi.TypeSchema;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.db.repo.EndpointRepo;
import com.ke.bella.openapi.db.repo.Page;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.metadata.EndpointDetails;
import com.ke.bella.openapi.metadata.MetaDataOps;
import com.ke.bella.openapi.metadata.MetadataFeatures;
import com.ke.bella.openapi.metadata.Model;
import com.ke.bella.openapi.metadata.PriceDetails;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.IModelFeatures;
import com.ke.bella.openapi.protocol.IModelProperties;
import com.ke.bella.openapi.protocol.IPriceInfo;
import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.ke.bella.openapi.tables.pojos.EndpointDB;
import com.ke.bella.openapi.utils.GroovyExecutor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

/**
 * Author: Stan Sai Date: 2024/8/2 10:41 description:
 */
@Component
public class EndpointService {
    @Autowired
    private EndpointRepo endpointRepo;
    @Autowired
    private ModelService modelService;
    @Autowired
    private ChannelService channelService;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private CacheManager cacheManager;
    @Value("${cost.script.limit.time:1000}")
    private int costScriptLimitMs;
    @Value("${cost.script.limit.memory:2000000}")
    private int costScriptLimitBytes;

    private static final String endpointCostScript = "endpoint:cost:script:";

    @PostConstruct
    public void postConstruct() {
        QuickConfig quickConfig = QuickConfig.newBuilder(endpointCostScript)
                .cacheNullValue(true)
                .cacheType(CacheType.BOTH)
                .syncLocal(true)
                .localExpire(Duration.ofSeconds(30))
                .penetrationProtect(true)
                .penetrationProtectTimeout(Duration.ofSeconds(10))
                .build();
        cacheManager.getOrCreateCache(quickConfig);
    }

    @Transactional
    public EndpointDB createEndpoint(MetaDataOps.EndpointOp op) {
        endpointRepo.checkExist(op.getEndpoint(), false);
        checkCostScript(op);
        EndpointDB db = endpointRepo.insert(op);
        cacheManager.getCache(endpointCostScript).put(op.getEndpoint(), op.getCostScript());
        return db;
    }

    @Transactional
    public void updateEndpoint(MetaDataOps.EndpointOp op) {
        endpointRepo.checkExist(op.getEndpoint(), true);
        checkCostScript(op);
        endpointRepo.update(op, op.getEndpoint());
        if(op.getCostScript() != null) {
            cacheManager.getCache(endpointCostScript).put(op.getEndpoint(), op.getCostScript());
        }
    }

    @Transactional
    public void changeStatus(String endpoint, boolean active) {
        endpointRepo.checkExist(endpoint, true);
        String status = active ? ACTIVE : INACTIVE;
        endpointRepo.updateStatus(endpoint, status);
    }

    public EndpointDB getActiveByEndpoint(String endpoint) {
        return getActive(UniqueKeyQuery.builder()
                .endpoint(endpoint)
                .build());
    }

    public EndpointDB getOne(UniqueKeyQuery query) {
        if(StringUtils.isNotEmpty(query.getEndpoint())) {
            return endpointRepo.queryByUniqueKey(query.getEndpoint());
        }
        if(StringUtils.isNotEmpty(query.getEndpointCode())) {
            return endpointRepo.queryByEndpointCode(query.getEndpointCode());
        }
        return null;
    }

    public EndpointDB getActive(UniqueKeyQuery query) {
        EndpointDB db = getOne(query);
        return db == null || db.getStatus().equals(INACTIVE) ? null : db;
    }

    @Cached(name = "endpoint:details:", key = "#condition.endpoint + ':' + #identity",
            cacheType = CacheType.BOTH,
            condition = "(#condition.modelName == null || #condition.modelName == '') "
                    + "&& (#condition.features == null || #condition.features.isEmpty())")
    public EndpointDetails getEndpointDetails(Condition.EndpointDetailsCondition condition, String identity) {
        List<EnumDto> features = MetadataFeatures.listFeatures(condition.getEndpoint());
        EndpointDetails endpoint = EndpointDetails.builder()
                .endpoint(condition.getEndpoint())
                .features(features)
                .build();
        List<Model> models = searchModels(condition);
        endpoint.setModels(models);
        Class<? extends IPriceInfo> priceType = IPriceInfo.EndpointPriceInfoType.fetchType(endpoint.getEndpoint());
        if(priceType != null) {
            Map<String, PriceDetails> priceDetails;
            if(CollectionUtils.isNotEmpty(models)) {
                priceDetails = channelService.getPriceInfo(models.stream()
                        .map(model -> modelService.fetchTerminalModelName(model.getModelName()))
                        .collect(Collectors.toList()), priceType);
                endpoint.getModels().forEach(model -> {
                    String terminal = modelService.fetchTerminalModelName(model.getModelName());
                    model.setPriceDetails(priceDetails.get(terminal));
                });
            } else {
                priceDetails = channelService.getPriceInfo(Lists.newArrayList(endpoint.getEndpoint()), priceType);
                endpoint.setPriceDetails(priceDetails.get(endpoint.getEndpoint()));
            }
        }
        return endpoint;
    }

    @Cached(name = endpointCostScript, key = "#endpoint")
    public String fetchCostScript(String endpoint) {
        EndpointDB db = getActive(UniqueKeyQuery.builder().endpoint(endpoint).build());
        return db == null ? null : db.getCostScript();
    }


    private void checkCostScript(MetaDataOps.EndpointOp op) {
        if(op.getCostScript() == null) {
            return;
        }
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> price = JacksonUtils.toMap(op.getTestPriceInfo());
        params.put("price", price == null ? new HashMap<>() : price);
        params.put("usage", op.getTestUsage() == null ? new HashMap<>() : op.getTestUsage());
        Object result = GroovyExecutor.testScript(op.getCostScript(), params, costScriptLimitMs, costScriptLimitBytes);
        try {
            BigDecimal cost = BigDecimal.valueOf(Double.parseDouble(result.toString()));
            Assert.isTrue(cost.doubleValue() > 0, "脚本返回结果不符合预期, result is " + result);
        } catch (Exception e) {
            throw new BizParamCheckException("脚本返回结果不符合预期, result is " + result.toString());
        }
    }

    private List<Model> searchModels(Condition.EndpointDetailsCondition condition) {
        Condition.ModelCondition modelCondition = new Condition.ModelCondition();
        modelCondition.setEndpoint(condition.getEndpoint());
        modelCondition.setModelName(condition.getModelName());
        modelCondition.setStatus(ACTIVE);
        if(CollectionUtils.isNotEmpty(condition.getFeatures())) {
            modelCondition.setFeatures(new ArrayList<>());
            if(!MetadataFeatures.validate(condition.getFeatures())) {
                return Lists.newArrayList();
            }
            condition.getFeatures().forEach(f -> {
                if(f.equals(MetadataFeatures.OVERSEAS.getCode())) {
                    modelCondition.setDataDestination(OVERSEAS);
                } else if(f.equals(MetadataFeatures.MAINLAND.getCode())) {
                    modelCondition.setDataDestination(MAINLAND);
                } else if(f.equals(MetadataFeatures.INNER.getCode())) {
                    modelCondition.setDataDestination(INNER);
                } else if(f.equals(MetadataFeatures.PROTECTED.getCode())) {
                    modelCondition.setDataDestination(PROTECTED);
                }
                else if(f.equals(MetadataFeatures.LARGE_INPUT_CONTEXT.getCode())) {
                    modelCondition.setMaxInputTokensLimit(100000);
                } else if(f.equals(MetadataFeatures.LARGE_OUTPUT_CONTEXT.getCode())) {
                    modelCondition.setMaxOutputTokensLimit(8000);
                } else {
                    modelCondition.getFeatures().add(f);
                }
            });
        }
        return modelService.listByConditionWithPermission(modelCondition, false)
                .stream().map(db -> {
                    Model model = new Model();
                    model.setModelName(db.getModelName());
                    model.setDocumentUrl(db.getDocumentUrl());
                    model.setFeatures(db.getFeatures());
                    model.setProperties(db.getProperties());
                    return model;
                }).sorted(Comparator.comparing(Model::getModelName).reversed())
                .collect(Collectors.toList());
    }

    public List<EndpointDB> listByCondition(Condition.EndpointCondition condition) {
        return endpointRepo.list(condition);
    }

    public <T> List<T> listByCondition(Condition.EndpointCondition condition, Class<T> type) {
        return endpointRepo.list(condition, type);
    }

    public Page<EndpointDB> pageByCondition(Condition.EndpointCondition condition) {
        return endpointRepo.page(condition);
    }

    public JsonSchema getModelPropertySchema(Set<String> endpoints) {
        Set<TypeSchema> schemas = endpoints.stream()
                .map(IModelProperties.EndpointModelPropertyType::fetchType)
                .filter(Objects::nonNull)
                .map(JsonSchema::toSchema)
                .map(JsonSchema::getParams)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        return new JsonSchema(schemas);
    }

    public JsonSchema getModelFeatureSchema(Set<String> endpoints) {
        Set<TypeSchema> schemas = endpoints.stream()
                .map(IModelFeatures.EndpointModelFeatureType::fetchType)
                .filter(Objects::nonNull)
                .map(JsonSchema::toSchema)
                .map(JsonSchema::getParams)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        return new JsonSchema(schemas);
    }

    public Map<String, String> listProtocols(String entityType, String entityCode) {
        Map<String, IProtocolAdaptor> protocolAdaptors = adaptorManager.getProtocolAdaptors(fetchEndpoint(entityType, entityCode));
        Map<String, String> result = new HashMap<>();
        protocolAdaptors.forEach((k, v) -> result.put(k, v.getDescription()));
        return result;
    }


    public JsonSchema getPriceInfoSchema(String entityType, String entityCode) {
        String endpoint = fetchEndpoint(entityType, entityCode);
        Class<? extends IPriceInfo> type = IPriceInfo.EndpointPriceInfoType.fetchType(endpoint);
        if(type == null) {
            return null;
        }
        return JsonSchema.toSchema(type);
    }

    public JsonSchema getChannelInfo(String entityType, String entityCode, String protocol) {
        IProtocolAdaptor protocolAdaptor = adaptorManager.getProtocolAdaptor(fetchEndpoint(entityType, entityCode), protocol);
        return JsonSchema.toSchema(protocolAdaptor.getPropertyClass());
    }

    private String fetchEndpoint(String entityType, String entityCode) {
        if(entityType.equals(ENDPOINT)) {
            return entityCode;
        } else {
            return modelService.getAllEndpoints(entityCode).get(0);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UniqueKeyQuery {
        private String endpoint;
        private String endpointCode;
    }

}
