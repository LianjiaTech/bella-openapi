package com.ke.bella.openapi.service;

import com.alicp.jetcache.CacheManager;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.Cached;
import com.alicp.jetcache.template.QuickConfig;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.db.repo.ModelRepo;
import com.ke.bella.openapi.db.repo.Page;
import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.metadata.Channel;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.metadata.MetaDataOps;
import com.ke.bella.openapi.metadata.Model;
import com.ke.bella.openapi.metadata.ModelDetails;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.EndpointDB;
import com.ke.bella.openapi.tables.pojos.ModelAuthorizerRelDB;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import com.ke.bella.openapi.tables.pojos.ModelEndpointRelDB;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.common.EntityConstants.ACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.INACTIVE;
import static com.ke.bella.openapi.common.EntityConstants.MODEL;
import static com.ke.bella.openapi.common.EntityConstants.PRIVATE;
import static com.ke.bella.openapi.common.EntityConstants.PUBLIC;
import static com.ke.bella.openapi.console.MetadataValidator.generateInvalidModelJsonKeyMessage;
import static com.ke.bella.openapi.console.MetadataValidator.json2Map;
import static com.ke.bella.openapi.console.MetadataValidator.matchPath;

/**
 * Author: Stan Sai Date: 2024/8/2 11:31 description:
 */
@Slf4j
@Component
public class ModelService {
    @Autowired
    private ModelRepo modelRepo;
    @Autowired
    private EndpointService endpointService;
    @Autowired
    private ChannelService channelService;
    @Autowired
    private ApikeyService apikeyService;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private ApplicationContext applicationContext;
    private static final String modelTerminalCacheKey = "model:terminal:";
    private static final String modelMapCacheKey = "model:map:";


    @PostConstruct
    public void postConstruct() {
        QuickConfig modelTerminalConfig = QuickConfig.newBuilder(modelTerminalCacheKey)
                .localLimit(500)
                .cacheNullValue(true)
                .cacheType(CacheType.BOTH)
                .syncLocal(true)
                .penetrationProtect(true)
                .penetrationProtectTimeout(Duration.ofSeconds(10))
                .build();
        cacheManager.getOrCreateCache(modelTerminalConfig);
        QuickConfig modelCacheConfig = QuickConfig.newBuilder(modelMapCacheKey)
                .expire(Duration.ofDays(3650))
                .localExpire(Duration.ofMinutes(5))
                .localLimit(1)
                .cacheNullValue(true)
                .cacheType(CacheType.BOTH)
                .syncLocal(true)
                .penetrationProtect(true)
                .penetrationProtectTimeout(Duration.ofSeconds(10))
                .build();
        cacheManager.getOrCreateCache(modelCacheConfig);
    }

    @Transactional
    public ModelDB createModel(MetaDataOps.ModelOp op) {
        modelRepo.checkExist(op.getModelName(), false);
        checkEndpoint(op.getEndpoints());
        checkPropertyAndFeatures(op.getProperties(), op.getFeatures(), op.getEndpoints(), op.getModelName());
        ModelDB db = modelRepo.insert(op);
        modelRepo.batchInsertModelEndpoints(op.getModelName(), op.getEndpoints());
        updateModelCache(op.getModelName(), op.getModelName());
        return db;
    }

    @Transactional
    public void updateModel(MetaDataOps.ModelOp op) {
        modelRepo.checkExist(op.getModelName(), true);
        if(CollectionUtils.isNotEmpty(op.getEndpoints())) {
            checkEndpoint(op.getEndpoints());
            List<ModelEndpointRelDB> originEndpoints = modelRepo.listEndpointsByModelName(op.getModelName());
            Set<String> inserts = Sets.newHashSet(op.getEndpoints());
            List<Long> deletes = new ArrayList<>();
            originEndpoints.forEach(origin -> {
                        if(op.getEndpoints().contains(origin.getEndpoint())) {
                            inserts.remove(origin.getEndpoint());
                        } else {
                            deletes.add(origin.getId());
                        }
                    }
            );
            if(CollectionUtils.isNotEmpty(deletes)) {
                modelRepo.batchDeleteModelEndpoints(deletes);
            }
            if(CollectionUtils.isNotEmpty(inserts)) {
                modelRepo.batchInsertModelEndpoints(op.getModelName(), inserts);
            }
        }
        checkPropertyAndFeatures(op.getProperties(), op.getFeatures(), op.getEndpoints(), op.getModelName());
        if(StringUtils.isNotEmpty(op.getDocumentUrl()) || StringUtils.isNotEmpty(op.getProperties())
                || StringUtils.isNotEmpty(op.getFeatures())) {
            modelRepo.update(op, op.getModelName());
        }
        updateModelCache(op.getModelName(), null);
    }

    private void checkPropertyAndFeatures(String properties, String features, Set<String> endpoints, String model) {
        if(properties == null  && features == null) {
            return;
        }
        List<String> basisEndpoints = getAllBasicEndpoints(endpoints, model);
        if(properties != null) {
            checkJson(properties, "properties", basisEndpoints);
        }
        if(features != null) {
            checkJson(features, "features", basisEndpoints);
        }
    }

    private void checkJson(String jsonStr, String field, List<String> basisEndpoints) {
        if(jsonStr.equals("{}")) {
            return;
        }
        Map<String, Object> map = json2Map(jsonStr);
        Assert.isTrue(map != null && !map.isEmpty(), field + "非json格式");
        for (String endpoint : basisEndpoints) {
            String invalidMessage = generateInvalidModelJsonKeyMessage(map, endpoint, field);
            Assert.isNull(invalidMessage, invalidMessage);
        }
    }

    private List<String> getAllBasicEndpoints(Set<String> endpoints, String model) {
        List<ModelEndpointRelDB> dbs = modelRepo.listEndpointsByModelName(model);
        Set<String> set = dbs.stream().map(ModelEndpointRelDB::getEndpoint).collect(Collectors.toSet());
        if(CollectionUtils.isNotEmpty(endpoints)) {
            set.addAll(endpoints);
        }
        return set.stream().filter(path ->
                Arrays.stream(EntityConstants.SystemBasicEndpoint.values())
                        .map(EntityConstants.SystemBasicEndpoint::getEndpoint)
                        .anyMatch(match -> matchPath(match, path))).collect(Collectors.toList());
    }

    public List<String> getAllEndpoints(String model) {
        List<ModelEndpointRelDB> dbs = modelRepo.listEndpointsByModelName(model);
        return dbs.stream().map(ModelEndpointRelDB::getEndpoint).collect(Collectors.toList());
    }

    public List<String> listModelNamesByEndpoint(String endpoint) {
        return modelRepo.listModelNamesByEndpoint(endpoint);
    }

    private void checkEndpoint(Set<String> endpoints) {
        Condition.EndpointCondition condition = Condition.EndpointCondition.builder()
                .endpoints(endpoints)
                .build();
        List<EndpointDB> dbs = endpointService.listByCondition(condition);
        Assert.isTrue(dbs.size() == endpoints.size(), () -> {
            Set<String> temp = Sets.newHashSet(endpoints);
            dbs.stream().map(EndpointDB::getEndpoint).collect(Collectors.toList()).forEach(temp::remove);
            return "能力点不存在：\r\n" + String.join(",", temp);
        });
    }

    @Transactional
    public void changeStatus(String modelName, boolean active) {
        modelRepo.checkExist(modelName, true);
        String status = active ? ACTIVE : INACTIVE;
        modelRepo.updateStatus(modelName, status);
        updateModelCache(modelName, null);
    }

    @Transactional
    public void changeVisibility(String modelName, boolean publish) {
        modelRepo.checkExist(modelName, true);
        String visibility = publish ? PUBLIC : PRIVATE;
        modelRepo.updateVisibility(modelName, visibility);
        updateModelCache(modelName, null);
    }

    @Transactional
    public void modelAuthorize(MetaDataOps.ModelAuthorizerOp op) {
        modelRepo.checkExist(op.getModel(), true);
        checkOwnerPermission(op.getModel());
        List<ModelAuthorizerRelDB> origins = modelRepo.listAuthorizersByModelName(op.getModel());
        Set<MetaDataOps.ModelAuthorizer> inserts = Sets.newHashSet(op.getAuthorizers());
        List<Long> deletes = new ArrayList<>();
        origins.forEach(origin -> {
                    MetaDataOps.ModelAuthorizer authorizer = MetaDataOps.ModelAuthorizer
                            .builder()
                            .authorizerType(origin.getAuthorizerType())
                            .authorizerCode(origin.getAuthorizerCode())
                            .build();
                    if(op.getAuthorizers().contains(authorizer)) {
                        inserts.remove(authorizer);
                    } else {
                        deletes.add(origin.getId());
                    }
                }
        );
        if(CollectionUtils.isNotEmpty(deletes)) {
            modelRepo.batchDeleteModelAuthorizers(deletes);
        }
        if(CollectionUtils.isNotEmpty(inserts)) {
            modelRepo.batchInsertModelAuthorizers(op.getModel(), inserts);
        }
    }

    @Transactional
    public void modelLink(MetaDataOps.ModelLinkOp op) {
        cacheManager.getCache(modelMapCacheKey).tryLockAndRun("lock", 10, TimeUnit.SECONDS, ()-> {
            doModelLink(op);
        });
    }

    private void doModelLink(MetaDataOps.ModelLinkOp op) {
        ModelDB db = modelRepo.queryByUniqueKeyForUpdateNoWait(op.getModelName());
        Assert.isTrue(db != null, "实体不存在");
        if(db.getLinkedTo().equals(op.getLinkedTo())) {
            return;
        }
        String terminal = op.getModelName();
        if(StringUtils.isNotEmpty(op.getLinkedTo())) {
            List<String> path = getPath(op.getLinkedTo());
            Assert.isTrue(CollectionUtils.isNotEmpty(path), "linedTo实体不存在");
            Assert.isTrue(!path.contains(op.getModelName()), "出现循环");
            terminal = path.get(path.size() - 1);
        }
        modelRepo.update(op, op.getModelName());
        doUpdateModelCache(op.getModelName(), terminal);
    }

    @Cached(name = modelTerminalCacheKey, key = "#modelName")
    public String fetchTerminalModelName(String modelName) {
        List<String> path = getPath(modelName);
        return CollectionUtils.isEmpty(path) ? modelName : path.get(path.size() - 1);
    }


    @Cached(name = modelMapCacheKey, key = "#key")
    public Map<String, ModelDB> queryWithCache(String key) {
        List<ModelDB> list = modelRepo.listAll();
        return list.stream().collect(Collectors.toMap(ModelDB::getModelName, Function.identity()));
    }

    public void refreshModelMap() {
        List<ModelDB> list = modelRepo.listAll();
        Map<String, ModelDB> map = list.stream().collect(Collectors.toMap(ModelDB::getModelName, Function.identity()));
        cacheManager.getCache(modelMapCacheKey).put("all", map);
    }

    public List<String> getPath(String target) {
        Map<String, ModelDB> map = applicationContext.getBean(ModelService.class).queryWithCache("all");
        return getPath(target, map);
    }

    private List<String> getPath(String target, Map<String, ModelDB> map) {
        List<String> path = new ArrayList<>();
        String name = target;
        while(StringUtils.isNotEmpty(name) && map.containsKey(name)) {
            path.add( name);
            name = map.get(name).getLinkedTo();
        }
        return path;
    }

    private String getTerminalName(String modelName, Map<String, ModelDB> map)  {
        List<String> path = getPath(modelName, map);
        return CollectionUtils.isEmpty(path) ? modelName : path.get(path.size() - 1);
    }

     public ModelDetails getModelDetails(String modelName) {
        Model model = modelRepo.queryByUniqueKey(modelName, Model.class);
        Assert.notNull(model, "实体不存在");
        List<Channel> channels = channelService
                .listByCondition(Condition.ChannelCondition.builder().entityType("model").entityCode(modelName)
                        .visibility(PUBLIC)
                        .build(), Channel.class);
        ModelDetails modelDetails = new ModelDetails();
        modelDetails.setChannels(channels);
        modelDetails.setModel(model);
        return modelDetails;
    }

    private void updateModelCache(String modelName, String terminal) {
        cacheManager.getCache(modelMapCacheKey).tryLockAndRun("lock", 10, TimeUnit.SECONDS, ()-> {
            doUpdateModelCache(modelName, terminal);
        });
    }

    private void doUpdateModelCache(String modelName, String terminal) {
        ModelDB modelDB = modelRepo.queryByUniqueKey(modelName);
        Map<String, ModelDB> map = applicationContext.getBean(ModelService.class).queryWithCache("all");
        map.put(modelName, modelDB);
        cacheManager.getCache(modelMapCacheKey).put("all", map);
        if(StringUtils.isNotEmpty(terminal)) {
            cacheManager.getCache(modelTerminalCacheKey).put(modelName, terminal);
        }
    }

    private void checkOwnerPermission(String model) {
        ModelDB db = modelRepo.queryByUniqueKey(model);
        Operator operator = BellaContext.getOperator();
        //todo: 检查 operator 是否是 Owner
    }

    public ModelDB getActiveByModelName(String modelName) {
        ModelDB db = getOne(modelName);
        return db == null || db.getStatus().equals(INACTIVE) ? null : db;
    }

    public ModelDB getOne(String modelName) {
        return modelRepo.queryByUniqueKey(modelName);
    }

    public ModelDB getOneForUpdate(String modelName) {
        return modelRepo.queryByUniqueKeyForUpdate(modelName);
    }

    public Model getInfo(String modelName) {
        ModelDB db = getOne(modelName);
        Model model = new Model();
        BeanUtils.copyProperties(db, model);
        convertToModelInfo(Lists.newArrayList(model));
        return model;
    }

    public List<ModelDB> listByCondition(Condition.ModelCondition condition) {
        fillModelNames(condition);
        return modelRepo.list(condition);
    }

    public Page<ModelDB> pageByCondition(Condition.ModelCondition condition) {
        return modelRepo.page(condition);
    }

    public List<Model> listByConditionForSelectList(Condition.ModelCondition condition) {
        if(!fillModelNames(condition)) {
            return Lists.newArrayList();
        }
        Map<String, ModelDB> map = applicationContext.getBean(ModelService.class).queryWithCache("all");
        return listByCondition(condition).stream()
                .map(db->{
                    Model model = new Model();
                    model.setModelName(db.getModelName());
                    model.setTerminalModel(getTerminalName(db.getModelName(), map));
                    return model;
                })
                .collect(Collectors.toList());
    }

    public List<Model> listByConditionWithPermission(Condition.ModelCondition condition, boolean apikeyFirst) {
        apikeyService.fillPermissionCode(condition, apikeyFirst);
        if(!fillModelNames(condition)) {
            return Lists.newArrayList();
        }

        List<Model> models = listByCondition(condition).stream()
                .map(db -> {
                    Model model = new Model();
                    BeanUtils.copyProperties(db, model);
                    return model;
                })
                .collect(Collectors.toList());

        if (condition.isIncludePrice() && !models.isEmpty()) {
            convertToModelInfo(models);
        }

        return models;
    }

    private void convertToModelInfo(List<Model> models) {
        try {
            Map<String, String> priceJsonMap = fetchModelPriceInfos(
                models.stream()
                    .map(Model::getModelName)
                    .collect(Collectors.toList())
            );

            models.forEach(model -> {
                String priceJson = priceJsonMap.get(model.getModelName());
                if (priceJson != null) {
                    model.setPriceInfo(priceJson);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to enrich models with price info: {}", e.getMessage());
        }
    }

    private Map<String, String> fetchModelPriceInfos(List<String> modelNames) {
        if (CollectionUtils.isEmpty(modelNames)) {
            return new HashMap<>();
        }

        Map<String, String> modelToTerminalMap = new HashMap<>();
        for (String modelName : modelNames) {
            String terminal = fetchTerminalModelName(modelName);
            modelToTerminalMap.put(modelName, terminal);
        }

        List<String> terminalModelNames = modelToTerminalMap.values().stream()
                .distinct()
                .collect(Collectors.toList());

        if (terminalModelNames.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, String> terminalPriceMap = channelService.getPriceInfoAsJson(terminalModelNames);

        Map<String, String> priceJsonMap = new HashMap<>();
        for (String modelName : modelNames) {
            String terminal = modelToTerminalMap.get(modelName);
            String priceJson = terminalPriceMap.get(terminal);
            if (priceJson != null) {
                priceJsonMap.put(modelName, priceJson);
            }
        }

        return priceJsonMap;
    }

    public Page<ModelDB> pageByConditionWithPermission(Condition.ModelCondition condition, boolean apikeyFirst) {
        apikeyService.fillPermissionCode(condition, apikeyFirst);
        if(!fillModelNames(condition)) {
            return new Page<>();
        }
        return pageByCondition(condition);
    }

    private boolean fillModelNames(Condition.ModelCondition condition) {
        if(StringUtils.isEmpty(condition.getDataDestination()) && StringUtils.isEmpty(condition.getSupplier())) {
            return true;
        }
        Condition.ChannelCondition channelCondition = new Condition.ChannelCondition();
        channelCondition.setStatus(ACTIVE);
        channelCondition.setDataDestination(condition.getDataDestination());
        channelCondition.setSupplier(condition.getSupplier());
        channelCondition.setEntityType(MODEL);
        List<ChannelDB> channels = channelService.listByCondition(channelCondition);
        Set<String> modelNames = channels.stream()
                .map(ChannelDB::getEntityCode)
                .filter(entityCode -> CollectionUtils.isEmpty(condition.getModelNames())
                        || condition.getModelNames().contains(entityCode))
                .collect(Collectors.toSet());
        condition.setModelNames(modelNames);
        condition.setIncludeLinkedTo(true);
        return CollectionUtils.isNotEmpty(modelNames);
    }
}
