package com.ke.bella.openapi.console;

import com.ke.bella.openapi.JsonSchema;
import com.ke.bella.openapi.annotations.BellaAPI;
import com.ke.bella.openapi.metadata.Channel;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.metadata.MetaDataOps;
import com.ke.bella.openapi.metadata.ModelDetails;
import com.ke.bella.openapi.service.CategoryService;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.EndpointService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.CategoryDB;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.EndpointDB;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
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

import static com.ke.bella.openapi.console.MetadataValidator.checkCategoryCreateOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkCategoryStatus;
import static com.ke.bella.openapi.console.MetadataValidator.checkChannelCreateOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkChannelStatusOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkChannelUpdateOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkEndpointCategoryOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkEndpointOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkEndpointStatusOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkModelAuthorizerOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkModelLinkOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkModelNameOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkModelOp;
import static com.ke.bella.openapi.console.MetadataValidator.checkReplaceEndpointCategoryOp;

@BellaAPI
@RestController
@RequestMapping("/console")
@Tag(name = "元数据管理")
public class MetadataConsoleController {
    @Autowired
    private EndpointService endpointService;
    @Autowired
    private ModelService modelService;
    @Autowired
    private ChannelService channelService;
    @Autowired
    private CategoryService categoryService;

    @PostMapping("/endpoint")
    public EndpointDB createEndpoint(@RequestBody MetaDataOps.EndpointOp op) {
        checkEndpointOp(op, true);
        return endpointService.createEndpoint(op);
    }

    @PutMapping("/endpoint")
    public Boolean updateEndpoint(@RequestBody MetaDataOps.EndpointOp op) {
        checkEndpointOp(op, false);
        endpointService.updateEndpoint(op);
        return true;
    }

    @PostMapping("/endpoint/activate")
    public Boolean activateEndpoint(@RequestBody MetaDataOps.EndpointStatusOp op) {
        checkEndpointStatusOp(op);
        endpointService.changeStatus(op.getEndpoint(), true);
        return true;
    }

    @PostMapping("/endpoint/inactivate")
    public Boolean inactivateEndpoint(@RequestBody MetaDataOps.EndpointStatusOp op) {
        checkEndpointStatusOp(op);
        endpointService.changeStatus(op.getEndpoint(), false);
        return true;
    }

    @PostMapping("/model")
    public ModelDB createModel(@RequestBody MetaDataOps.ModelOp op) {
        checkModelOp(op, true);
        return modelService.createModel(op);
    }

    @PutMapping("/model")
    public Boolean updateModel(@RequestBody MetaDataOps.ModelOp op) {
        checkModelOp(op, false);
        modelService.updateModel(op);
        return true;
    }

    @PostMapping("/model/activate")
    public Boolean activateModel(@RequestBody MetaDataOps.ModelStatusOp op) {
        checkModelNameOp(op);
        modelService.changeStatus(op.getModelName(), true);
        return true;
    }

    @PostMapping("/model/inactivate")
    public Boolean inactivateModel(@RequestBody MetaDataOps.ModelStatusOp op) {
        checkModelNameOp(op);
        modelService.changeStatus(op.getModelName(), false);
        return true;
    }

    @PostMapping("/model/publish")
    public Boolean publishModel(@RequestBody MetaDataOps.ModelVisibilityOp op) {
        checkModelNameOp(op);
        modelService.changeVisibility(op.getModelName(), true);
        return true;
    }

    @PostMapping("/model/publish/cancel")
    public Boolean cancelPublishModel(@RequestBody MetaDataOps.ModelVisibilityOp op) {
        checkModelNameOp(op);
        modelService.changeVisibility(op.getModelName(), false);
        return true;
    }

    @PostMapping("/model/authorize")
    public Boolean authorizeModel(@RequestBody MetaDataOps.ModelAuthorizerOp op) {
        checkModelAuthorizerOp(op);
        modelService.modelAuthorize(op);
        return true;
    }

    @PostMapping("/model/link")
    public Boolean linkModel(@RequestBody MetaDataOps.ModelLinkOp op) {
        checkModelLinkOp(op);
        modelService.modelLink(op);
        return true;
    }

    @PostMapping("/channel")
    public ChannelDB createChannel(@RequestBody MetaDataOps.ChannelCreateOp op) {
        checkChannelCreateOp(op);
        return channelService.createChannel(op);
    }

    @PutMapping("/channel")
    public Boolean updateChannel(@RequestBody MetaDataOps.ChannelUpdateOp op) {
        checkChannelUpdateOp(op);
        channelService.updateChannel(op);
        return true;
    }

    @GetMapping("/channels/{queueName}")
    public Channel getChannelByQueue(@PathVariable String queueName) {
        return channelService.getByQueueName(queueName);
    }

    @PostMapping("/channel/activate")
    public Boolean activateChannel(@RequestBody MetaDataOps.ChannelStatusOp op) {
        checkChannelStatusOp(op);
        channelService.changeStatus(op.getChannelCode(), true);
        return true;
    }

    @PostMapping("/channel/inactivate")
    public Boolean inactivateChannel(@RequestBody MetaDataOps.ChannelStatusOp op) {
        checkChannelStatusOp(op);
        channelService.changeStatus(op.getChannelCode(), false);
        return true;
    }

    @PostMapping("/category")
    public CategoryDB createCategory(@RequestBody MetaDataOps.CategoryCreateOp op) {
        checkCategoryCreateOp(op);
        return categoryService.createCategory(op);
    }

    @PostMapping("/category/activate")
    public Boolean activateCategory(@RequestBody MetaDataOps.CategoryStatusOp op) {
        checkCategoryStatus(op);
        categoryService.changeStatus(op.getCategoryCode(), true);
        return true;
    }

    @PostMapping("/category/inactivate/")
    public Boolean inactivateCategory(@RequestBody MetaDataOps.CategoryStatusOp op) {
        checkCategoryStatus(op);
        categoryService.changeStatus(op.getCategoryCode(), false);
        return true;
    }

    @PostMapping("/endpoint/category")
    public Boolean addCategoryWithEndpoint(@RequestBody MetaDataOps.EndpointCategoriesOp op) {
        checkEndpointCategoryOp(op);
        categoryService.addCategoriesWithEndpoint(op);
        return true;
    }

    @DeleteMapping("/endpoint/category")
    public Boolean removeCategoryWithEndpoint(@RequestBody MetaDataOps.EndpointCategoriesOp op) {
        checkEndpointCategoryOp(op);
        categoryService.removeCategoriesWithEndpoint(op);
        return true;
    }

    @PostMapping("/endpoint/category/replace")
    public Boolean replaceCategoryWithEndpoint(@RequestBody MetaDataOps.EndpointCategoriesOp op) {
        checkReplaceEndpointCategoryOp(op);
        categoryService.replaceCategoryWithEndpoint(op);
        return true;
    }

    @GetMapping("/model/details")
    public ModelDetails getModelDetails(@RequestParam String modelName) {
        return modelService.getModelDetails(modelName);
    }

    @GetMapping("/model/list")
    public List<ModelDB> listModels(Condition.ModelCondition condition) {
        return modelService.listByCondition(condition);
    }
}
