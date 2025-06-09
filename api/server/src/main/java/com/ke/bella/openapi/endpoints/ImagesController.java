package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.images.ImagesAdaptor;
import com.ke.bella.openapi.protocol.images.ImagesProperty;
import com.ke.bella.openapi.protocol.images.ImagesRequest;
import com.ke.bella.openapi.protocol.images.ImagesResponse;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@EndpointAPI
@RestController
@RequestMapping("/v1/images")
@Tag(name = "images")
@Slf4j
public class ImagesController {
    
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointLogger logger;
    @Autowired
    private ISafetyCheckService.IImagesSafetyCheckService safetyCheckService;
    @Value("${bella.openapi.max-models-per-request:3}")
    private Integer maxModelsPerRequest;
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/generations")
    public Object generateImages(@RequestBody ImagesRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        
        // 参数验证
        if (StringUtils.isBlank(request.getPrompt())) {
            throw new BizParamCheckException("prompt不能为空");
        }
        
        // Handle multi-model requests
        if (model != null && model.contains(",")) {
            // Try each model in order
            String[] models = model.split(",");
            int maxNum = models.length;
            // Validate model count
            if (models.length > maxModelsPerRequest) {
                log.warn("请求模型数量超过最大限制: " + maxModelsPerRequest);
                maxNum = maxModelsPerRequest;
            }
            Exception lastException = null;
            
            for (int i = 0; i < maxNum; i++) {
                String singleModel = models[i];
                String trimmedModel = singleModel.trim();
                // Create a copy of the request with just this model
                request.setModel(trimmedModel);
                
                try {
                    // Try to process with this model
                    return processImagesRequest(endpoint, trimmedModel, request);
                } catch (Exception e) {
                    // Log the exception and continue to the next model
                    lastException = e;
                }
            }
            
            // If we get here, all models failed
            if (lastException != null) {
                throw ChannelException.fromException(lastException);
            } else {
                throw new BizParamCheckException("所有指定的模型都无法处理请求");
            }
        }
        
        // Single model processing
        return processImagesRequest(endpoint, model, request);
    }
    
    private Object processImagesRequest(String endpoint, String model, ImagesRequest request) {
        EndpointContext.setEndpointData(endpoint, model, request);
        boolean isMock = EndpointContext.getProcessData().isMock();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), isMock);
        EndpointContext.setEndpointData(channel);
        
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        
        Object requestRiskData = safetyCheckService.safetyCheck(SafetyCheckRequest.Images.convertFrom(request,
                    EndpointContext.getProcessData(), EndpointContext.getApikey()), isMock);
        EndpointContext.getProcessData().setRequestRiskData(requestRiskData);
        
        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        
        ImagesAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, ImagesAdaptor.class);
        ImagesProperty property = (ImagesProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        
        if(isMock) {
            fillMockProperty(property);
        }
        
        EndpointContext.setEncodingType(property.getEncodingType());
        
        ImagesResponse response = adaptor.generateImages(request, url, property);
        Object responseRiskData = safetyCheckService.safetyCheck(SafetyCheckRequest.Images.convertFrom(response, EndpointContext.getProcessData(), EndpointContext.getApikey()), isMock);
        response.setSensitives(responseRiskData);
        response.setRequestRiskData(requestRiskData);
        return response;
    }
    
    private void fillMockProperty(ImagesProperty property) {
        Map<String, String> requestInfo = BellaContext.getHeaders();
        // 可以根据需要添加mock属性设置
    }
}
