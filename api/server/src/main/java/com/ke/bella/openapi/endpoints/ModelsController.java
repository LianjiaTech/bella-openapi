package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.metadata.Condition;
import com.ke.bella.openapi.metadata.Model;
import com.ke.bella.openapi.protocol.model.ModelInfo;
import com.ke.bella.openapi.protocol.model.ModelListResponse;
import com.ke.bella.openapi.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@EndpointAPI
@RestController
@RequestMapping("/v1/models")
@Tag(name = "models", description = "Model listing operations")
public class ModelsController {

    @Autowired
    private ModelService modelService;

    @GetMapping
    @Operation(summary = "List models", description = "Lists the currently available models with basic information including owner and availability")
    public ModelListResponse listModels() {
        Condition.ModelCondition condition = new Condition.ModelCondition();
        condition.setStatus(EntityConstants.ACTIVE);
        List<Model> models = modelService.listByConditionWithPermission(condition, true);
        List<ModelInfo> modelInfoList = models.stream()
                .map(ModelsController::toModelInfo)
                .collect(Collectors.toList());
        return new ModelListResponse(modelInfoList);
    }

    private static ModelInfo toModelInfo(Model model) {
        Long created = model.getCtime() != null
                ? model.getCtime().atZone(ZoneId.systemDefault()).toEpochSecond()
                : System.currentTimeMillis() / 1000;
        String ownedBy = model.getOwnerName() != null && !model.getOwnerName().isEmpty()
                ? model.getOwnerName()
                : model.getOwnerCode();
        return ModelInfo.builder()
                .id(model.getModelName())
                .created(created)
                .ownedBy(ownedBy)
                .build();
    }
}