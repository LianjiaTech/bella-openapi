package com.ke.bella.openapi.service;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.ke.bella.openapi.console.MetaDataOps;
import com.ke.bella.openapi.db.TableConstants;
import com.ke.bella.openapi.db.repo.ModelRepo;
import com.ke.bella.openapi.db.repo.Page;
import com.ke.bella.openapi.protocol.Condition;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.EndpointDB;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import com.ke.bella.openapi.tables.pojos.ModelEndpointRelDB;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.console.MetadataValidator.*;
import static com.ke.bella.openapi.db.TableConstants.ACTIVE;
import static com.ke.bella.openapi.db.TableConstants.INACTIVE;
import static com.ke.bella.openapi.db.TableConstants.MODEL;
import static com.ke.bella.openapi.db.TableConstants.PRIVATE;
import static com.ke.bella.openapi.db.TableConstants.PUBLIC;

/**
 * Author: Stan Sai Date: 2024/8/2 11:31 description:
 */
@Component
public class ModelService {
    @Autowired
    private ModelRepo modelRepo;
    @Autowired
    private EndpointService endpointService;
    @Autowired
    private ChannelService channelService;

    @Transactional
    public ModelDB createModel(MetaDataOps.ModelOp op) {
        modelRepo.checkExist(op.getModelName(), false);
        checkEndpoint(op.getEndpoints());
        checkPropertyAndFeatures(op.getProperties(), op.getFeatures(), op.getEndpoints(), op.getModelName());
        ModelDB db = modelRepo.insert(op);
        modelRepo.batchInsertModelEndpoints(op.getModelName(), op.getEndpoints());
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
    }

    private void checkPropertyAndFeatures(String properties, String features, Set<String> endpoints, String model) {
        if(StringUtils.isEmpty(properties) && StringUtils.isEmpty(features)) {
            return;
        }
        List<String> basisEndpoints = getAllBasicEndpoints(endpoints, model);
        if(StringUtils.isNotEmpty(properties)) {
            checkJson(properties, "properties", basisEndpoints);
        }
        if(StringUtils.isNotEmpty(features)) {
            checkJson(features, "features", basisEndpoints);
        }
    }

    private void checkJson(String obj, String field, List<String> basisEndpoints) {
        Map<String, Object> map = json2Map(obj);
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
                Arrays.stream(TableConstants.SystemBasicEndpoint.values())
                        .map(TableConstants.SystemBasicEndpoint::getEndpoint)
                        .anyMatch(match -> matchPath(match, path))).collect(Collectors.toList());
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
    }

    @Transactional
    public void changeVisibility(String modelName, boolean publish) {
        modelRepo.checkExist(modelName, true);
        String visibility = publish ? PUBLIC : PRIVATE;
        if(publish) {
            List<ChannelDB> actives = channelService.listActives(MODEL, modelName);
            Assert.notEmpty(actives, "模型至少有一个可用渠道才可以发布");
        }
        modelRepo.updateVisibility(modelName, visibility);
    }

    public ModelDB getActiveByModelName(String modelName) {
        ModelDB db = getOne(modelName);
        return db == null || db.getStatus().equals(INACTIVE) ? null : db;
    }

    public ModelDB getOne(String modelName) {
        return modelRepo.queryByUniqueKey(modelName);
    }

    public List<ModelDB> listByCondition(Condition.ModelCondition condition) {
        if(!fillModelNames(condition)) {
            return Lists.newArrayList();
        }
        return modelRepo.list(condition);
    }

    public Page<ModelDB> pageByCondition(Condition.ModelCondition condition) {
        if(!fillModelNames(condition)) {
            return Page.from(condition.getPageNum(), condition.getPageSize());
        }
        return modelRepo.page(condition);
    }

    private boolean fillModelNames(Condition.ModelCondition condition) {
        if(StringUtils.isNotEmpty(condition.getEndpoint())) {
            List<String> modelNames = modelRepo.listModelNamesByEndpoint(condition.getEndpoint());
            if(CollectionUtils.isEmpty(modelNames)) {
                return false;
            }
            if(condition.getModelNames() == null) {
                condition.setModelNames(new HashSet<>());
            }
            condition.getModelNames().addAll(modelNames);
        }
        return true;
    }

}
