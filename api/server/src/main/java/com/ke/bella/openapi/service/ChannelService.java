package com.ke.bella.openapi.service;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.alicp.jetcache.template.QuickConfig;
import com.ke.bella.openapi.db.repo.ChannelRepo;
import com.ke.bella.openapi.db.repo.Page;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.metadata.MetaDataOps;
import com.ke.bella.openapi.metadata.PriceDetails;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.IPriceInfo;
import com.ke.bella.openapi.protocol.cost.CostCalculator;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.EndpointDB;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.common.EntityConstants.ACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.ENDPOINT;
import static com.ke.bella.openapi.common.EntityConstants.INACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.MODEL;
import static com.ke.bella.openapi.common.EntityConstants.PUBLIC;

/**
 * Author: Stan Sai Date: 2024/8/2 11:35 description:
 */
@Component
public class ChannelService {
    @Autowired
    private ChannelRepo channelRepo;
    @Autowired
    private EndpointService endpointService;
    @Autowired
    private ModelService modelService;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private CacheManager cacheManager;
    private static final String channelCacheKey = "channels:active:";

    @PostConstruct
    public void postConstruct() {
        QuickConfig quickConfig = QuickConfig.newBuilder(channelCacheKey)
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
    public ChannelDB createChannel(MetaDataOps.ChannelCreateOp op) {
        List<String> endpoints = new ArrayList<>();
        if(op.getEntityType().equals(ENDPOINT)) {
            EndpointDB endpoint = endpointService.getOne(EndpointService.UniqueKeyQuery.builder()
                    .endpoint(op.getEntityCode()).build());
            Assert.notNull(endpoint, "能力点实体不存在");
            endpoints.add(endpoint.getEndpoint());
        } else {
            ModelDB model = modelService.getOne(op.getEntityCode());
            Assert.notNull(model, "模型实体不存在");
            endpoints = modelService.getAllEndpoints(model.getModelName());

        }
        endpoints.forEach(endpoint -> Assert.isTrue(CostCalculator.validate(endpoint, op.getPriceInfo()), "priceInfo invalid"));
        endpoints.forEach(endpoint -> Assert.isTrue(adaptorManager.support(endpoint, op.getProtocol()), "不支持的协议"));

        if (StringUtils.isEmpty(op.getVisibility())) {
            op.setVisibility(PUBLIC);
        }
        
        //todo: 根据协议检查channelInfo
        ChannelDB channelDB = channelRepo.insert(op);
        updateCache(channelDB.getEntityType(), channelDB.getEntityCode());
        return channelDB;
    }

    @Transactional
    public void updateChannel(MetaDataOps.ChannelUpdateOp op) {
        channelRepo.checkExist(op.getChannelCode(), true);
        if(StringUtils.isNotEmpty(op.getPriceInfo())) {
            List<String> endpoints = new ArrayList<>();
            Entity entity = getEntityInfoByCode(op.getChannelCode());
            if(entity.getEntityCode().equals(MODEL)) {
                ModelDB model = modelService.getOne(entity.getEntityCode());
                endpoints = modelService.getAllEndpoints(model.getModelName());
            } else {
                endpoints.add(entity.getEntityCode());
            }
            endpoints.forEach(endpoint -> Assert.isTrue(CostCalculator.validate(endpoint, op.getPriceInfo()), "priceInfo invalid"));
        }
        //todo: 根据协议检查channelInfo
        channelRepo.update(op, op.getChannelCode());
        updateCache(op.getChannelCode());
    }

    @Transactional
    public void changeStatus(String channelCode, boolean active) {
        channelRepo.checkExist(channelCode, true);
        String status = active ? ACTIVE : INACTIVE;
        channelRepo.updateStatus(channelCode, status);
        updateCache(channelCode);
    }

    public Entity getEntityInfoByCode(String code) {
        ChannelDB channel = channelRepo.queryByUniqueKey(code);
        return Entity.builder()
                .entityType(channel.getEntityType())
                .entityCode(channel.getEntityCode())
                .build();
    }

    public ChannelDB getOne(String channelCode) {
        return channelRepo.queryByUniqueKey(channelCode);
    }

    public ChannelDB getActiveByChannelCode(String channelCode) {
        ChannelDB db = getOne(channelCode);
        return db == null || db.getStatus().equals(INACTIVE) ? null : db;
    }

    public List<ChannelDB> listActivesWithDb(String entityType, String entityCode) {
        return listByCondition(Condition.ChannelCondition.builder()
                .status(ACTIVE)
                .entityType(entityType)
                .entityCode(entityCode)
                .build());
    }

    @Cached(name = channelCacheKey, key = "#entityType + ':' + #entityCode")
    public List<ChannelDB> listActives(String entityType, String entityCode) {
        if(entityType == null || entityCode == null) {
            return null;
        }
        return listActivesWithDb(entityType, entityCode);
    }

    public List<String> listSuppliers() {
        return channelRepo.listSuppliers();
    }

    private void updateCache(String channelCode) {
        ChannelDB db = channelRepo.queryByUniqueKey(channelCode);
        updateCache(db.getEntityType(), db.getEntityCode());
    }

    private void updateCache(String entityType, String entityCode) {
        List<ChannelDB> channels = listByCondition(Condition.ChannelCondition.builder()
                .status(ACTIVE)
                .entityType(entityType)
                .entityCode(entityCode)
                .build());
        Cache<String, List<ChannelDB>> cache = cacheManager.getCache(channelCacheKey);
        cache.put(entityType + ":" + entityCode, channels);
    }

    public List<ChannelDB> listByCondition(Condition.ChannelCondition condition) {
        return channelRepo.list(condition);
    }

    public <H> List<H> listByCondition(Condition.ChannelCondition condition, Class<H> type) {
        return channelRepo.list(condition, type);
    }

    public Page<ChannelDB> pageByCondition(Condition.ChannelCondition condition) {
        return channelRepo.page(condition);
    }

    public Map<String, PriceDetails> getPriceInfo(List<String> entityCodes, Class<? extends IPriceInfo> type) {
        Map<String, String> prices = channelRepo.queryPriceInfo(entityCodes);
        Map<String, Field> fields = Arrays.stream(type.getDeclaredFields()).map(field -> {
            field.setAccessible(true);
            return field;
        }).collect(Collectors.toMap(Field::getName, f -> f));
        Map<String, PriceDetails> result = new HashMap<>();
        prices.forEach((k, v) -> {
            IPriceInfo priceInfo = JacksonUtils.deserialize(v, type);
            if(priceInfo != null) {
                PriceDetails details = new PriceDetails();
                details.setUnit(priceInfo.getUnit());
                details.setPriceInfo(priceInfo);
                details.setDisplayPrice(new LinkedHashMap<>());
                priceInfo.description().forEach((filedName, desc) -> {
                    try {
                        Object val = fields.get(filedName).get(priceInfo);
                        if(val != null) {
                            details.getDisplayPrice().put(desc, val.toString());
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
                result.put(k, details);
            }
        });
        return result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Entity {
        private String entityType;
        private String entityCode;
    }
}
