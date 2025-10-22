package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.JsonSchema;
import com.ke.bella.openapi.Operator;
import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.console.MetadataValidator;
import com.ke.bella.openapi.db.repo.Page;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.metadata.EndpointCategoryTree;
import com.ke.bella.openapi.metadata.EndpointDetails;
import com.ke.bella.openapi.metadata.MetaDataOps;
import com.ke.bella.openapi.metadata.Model;
import com.ke.bella.openapi.protocol.tts.VoiceProperties;
import com.ke.bella.openapi.service.CategoryService;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.EndpointService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.CategoryDB;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.EndpointDB;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@BellaAPI
@RestController
@RequestMapping("/v1/meta")
@Tag(name = "信息查询")
public class MetadataController {
    @Autowired
    private EndpointService endpointService;
    @Autowired
    private ModelService modelService;
    @Autowired
    private ChannelService channelService;
    @Autowired
    private CategoryService categoryService;


    @GetMapping("/endpoint/details")
    public EndpointDetails listEndpointDetails(Condition.EndpointDetailsCondition condition) {
        Assert.notNull(condition.getEndpoint(), "能力点不可为空");
        String identity = BellaContext.getOperatorIgnoreNull() != null ?
                BellaContext.getOperator().getUserId().toString() : EndpointContext.getApikey().getCode();
        return endpointService.getEndpointDetails(condition, identity);
    }

    @GetMapping("/endpoint/list")
    public List<EndpointDB> listEndpoint(Condition.EndpointCondition condition) {
        return endpointService.listByCondition(condition);
    }

    @GetMapping("/endpoint/page")
    public Page<EndpointDB> pageEndpoint(Condition.EndpointCondition condition) {
        return endpointService.pageByCondition(condition);
    }

    @GetMapping("/endpoint/info/{code}")
    public EndpointDB getEndpoint(@PathVariable String code) {
        return endpointService.getOne(EndpointService.UniqueKeyQuery.builder().endpointCode(code).build());
    }

    @GetMapping("/model/list/for-selection")
    public List<Model> listModelForSelection(Condition.ModelCondition condition) {
        return modelService.listByConditionForSelectList(condition);
    }

    @GetMapping("/model/list")
    public List<ModelDB> listModel(Condition.ModelCondition condition) {
        return modelService.listByConditionWithPermission(condition, true);
    }

    @GetMapping("/model/page")
    public Page<ModelDB> pageModel(Condition.ModelCondition condition) {
        return modelService.pageByConditionWithPermission(condition, true);
    }

    @GetMapping("/model/info/{name}")
    public ModelDB getModel(@PathVariable String name) {
        return modelService.getOne(name);
    }

    @GetMapping("/channel/list")
    public List<ChannelDB> listChannel(Condition.ChannelCondition condition) {
        fillChannelCondition(condition);
        return channelService.listByCondition(condition);
    }

    @GetMapping("/channel/page")
    public Page<ChannelDB> pageChannel(Condition.ChannelCondition condition) {
        fillChannelCondition(condition);
        return channelService.pageByCondition(condition);
    }

    private void fillChannelCondition(Condition.ChannelCondition condition) {
        Operator operator = BellaContext.getOperatorIgnoreNull();
        String accountType = operator != null ? EntityConstants.PERSON : BellaContext.getApikey().getOwnerType();
        String accountCode = operator != null ? operator.getUserId().toString() : BellaContext.getApikey().getOwnerCode();
        condition.setVisibility(EntityConstants.PRIVATE);
        condition.setOwnerType(accountType);
        condition.setOwnerCode(accountCode);
    }

    @GetMapping("/category/list")
    public List<CategoryDB> listCategory(Condition.CategoryCondition condition) {
        return categoryService.listByCondition(condition);
    }

    @GetMapping("/category/page")
    public Page<CategoryDB> pageCategory(Condition.CategoryCondition condition) {
        return categoryService.pageByCondition(condition);
    }

    @GetMapping("/category/tree")
    public EndpointCategoryTree listTree(Condition.CategoryTreeCondition condition) {
        return categoryService.listTree(condition);
    }

    @GetMapping("/category/tree/all")
    public List<EndpointCategoryTree> listAllTree() {
        return categoryService.listAllTree();
    }

    @GetMapping("/supplier/list")
    public List<String> listSuppliers() {
        return channelService.listSuppliers();
    }

    @GetMapping("/schema/modelProperty")
    public JsonSchema getModelPropertySchema(@RequestParam Set<String> endpoints) {
        return endpointService.getModelPropertySchema(endpoints);
    }

    @GetMapping("/schema/modelFeature")
    public JsonSchema getModelFeatureSchema(@RequestParam Set<String> endpoints) {
        return endpointService.getModelFeatureSchema(endpoints);
    }

    @GetMapping("/schema/priceInfo")
    public JsonSchema getPriceInfoSchema(@RequestParam String entityType, @RequestParam String entityCode) {
        return endpointService.getPriceInfoSchema(entityType, entityCode);
    }

    @GetMapping("/schema/channelInfo")
    public JsonSchema getChannelInfoSchema(@RequestParam String entityType, @RequestParam String entityCode, @RequestParam String protocol) {
        return endpointService.getChannelInfo(entityType, entityCode, protocol);
    }

    @GetMapping("/protocol/list")
    public Map<String, String> listProtocols(@RequestParam String entityType, @RequestParam String entityCode) {
        return endpointService.listProtocols(entityType, entityCode);
    }
    
    @PostMapping("/channel/private")
    public ChannelDB createPrivateChannel(@RequestBody MetaDataOps.ChannelCreateOp op) {
        op.setVisibility(EntityConstants.PRIVATE);
        Operator operator = BellaContext.getOperatorIgnoreNull();
        if(StringUtils.isBlank(op.getOwnerType()) || StringUtils.isBlank(op.getOwnerCode())) {
            String accountType = operator != null ? EntityConstants.PERSON : BellaContext.getApikey().getOwnerType();
            String accountCode = operator != null ? operator.getUserId().toString() : BellaContext.getApikey().getOwnerCode();
            String accountName = operator != null ? operator.getUserName() : BellaContext.getApikey().getOwnerName();
            op.setOwnerType(accountType);
            op.setOwnerCode(accountCode);
            if(StringUtils.isNotBlank(accountName)) {
                op.setOwnerName(accountName);
            }
        }
        MetadataValidator.checkChannelCreateOp(op);
        return channelService.createChannel(op);
    }

    @PutMapping("/channel/private")
    public Boolean updatePrivateChannel(@RequestBody MetaDataOps.ChannelUpdateOp op) {
        checkChannel(op.getChannelCode());
        channelService.updateChannel(op);
        return true;
    }
    
    @PostMapping("/channel/private/activate")
    public Boolean activatePrivateChannel(@RequestBody MetaDataOps.ChannelStatusOp op) {
        checkChannel(op.getChannelCode());
        channelService.changeStatus(op.getChannelCode(), true);
        return true;
    }
    
    @PostMapping("/channel/private/inactivate")
    public Boolean inactivatePrivateChannel(@RequestBody MetaDataOps.ChannelStatusOp op) {
        checkChannel(op.getChannelCode());
        channelService.changeStatus(op.getChannelCode(), false);
        return true;
    }

    @GetMapping("/property/voice")
    public VoiceProperties fetchVoiceProperty(Condition.ChannelCondition condition) {
        String model = condition.getEntityCode();
        if(EntityConstants.ENDPOINT.equals(condition.getEntityType())) {
            model = null;
            List<ChannelDB> channels = channelService.listActives(condition.getEntityType(), condition.getEntityCode());
            if(CollectionUtils.isEmpty(channels)) {
                return null;
            }
            Map<String, Object> channelInfos = JacksonUtils.toMap(channels.get(0).getChannelInfo());
            if(MapUtils.isNotEmpty(channelInfos) && channelInfos.containsKey("modelName")) {
               model = channelInfos.get("modelName").toString();
            }
        }
        if(model == null) {
            return null;
        }
        ModelDB modelDB = modelService.getOne(model);
        if(modelDB == null) {
            return null;
        }
        return JacksonUtils.deserialize(modelDB.getProperties(), VoiceProperties.class);
    }

    private void checkChannel(String channelCode) {
        ChannelDB channel = channelService.getOne(channelCode);
        if (channel == null) {
            throw new BizParamCheckException("渠道不存在" );
        }
        if (EntityConstants.PUBLIC.equals(channel.getVisibility())) {
            throw new BizParamCheckException("只能修改私有渠道");
        }

        // 检查用户是否有权限更新该渠道
        Operator operator = BellaContext.getOperatorIgnoreNull();
        String accountType = operator != null ? EntityConstants.PERSON : BellaContext.getApikey().getOwnerType();
        String accountCode = operator != null ? operator.getUserId().toString() : BellaContext.getApikey().getOwnerCode();

        if (!channel.getOwnerType().equals(accountType) || !channel.getOwnerCode().equals(accountCode)) {
            throw new BizParamCheckException("只能修改自己的私有渠道");
        }
    }
}
