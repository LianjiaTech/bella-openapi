package com.ke.bella.openapi.endpoints;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ke.bella.queue.QueueClient;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptorDelegator;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.DirectPassthroughAdaptor;
import com.ke.bella.openapi.protocol.completion.QueueAdaptor;
import com.ke.bella.openapi.protocol.completion.ToolCallSimulator;
import com.ke.bella.openapi.protocol.completion.callback.StreamCallbackProvider;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.SseHelper;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@EndpointAPI
@RestController
@RequestMapping("/v1/chat")
@Tag(name = "chat")
@Slf4j
public class ChatController {
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointLogger logger;
    @Autowired
    private ISafetyCheckService.IChatSafetyCheckService safetyCheckService;
    @Autowired
    private EndpointDataService endpointDataService;
    @Value("${bella.openapi.max-models-per-request:3}")
    private Integer maxModelsPerRequest;
    @Autowired
    private QueueClient queueClient;

    @PostMapping("/completions")
    public Object completion(HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        String endpoint = httpRequest.getRequestURI();

        // Check for direct mode - skip body deserialization for performance
        if (BellaContext.isDirectMode()) {
            return processDirectModeRequest(endpoint, httpRequest, httpResponse);
        }

        // Normal mode - read and parse request body
        byte[] bodyBytes = IOUtils.toByteArray(httpRequest.getInputStream());
        CompletionRequest request = JacksonUtils.deserialize(bodyBytes, CompletionRequest.class);
        String model = request.getModel();
        
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
                CompletionRequest processedRequest = request.copyRequest();
                String singleModel = models[i];
                String trimmedModel = singleModel.trim();
                // Create a copy of the request with just this model
                processedRequest.setModel(trimmedModel);
                
                try {
                    // Try to process with this model
                    return processCompletionRequest(endpoint, trimmedModel, processedRequest);
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
        return processCompletionRequest(endpoint, model, request);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object processCompletionRequest(String endpoint, String model, CompletionRequest request) {
        endpointDataService.setEndpointData(endpoint, model, request);
        boolean isMock = EndpointContext.getProcessData().isMock();

        // Initialize channel using common method
        ChannelContext ctx = initializeChannel(endpoint, model, false);

        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        Object requestRiskData = safetyCheckService.safetyCheck(SafetyCheckRequest.Chat.convertFrom(request,
                    EndpointContext.getProcessData(), EndpointContext.getApikey()), isMock);
        EndpointContext.getProcessData().setRequestRiskData(requestRiskData);
        EndpointProcessData processData = EndpointContext.getProcessData();

        CompletionAdaptor adaptor = ctx.adaptor;
        CompletionProperty property = ctx.property;
        if(isMock) {
            fillMockProperty(property);
        }
        adaptor = decorateAdaptor(adaptor, property, processData);

        if(request.isStream()) {
            SseEmitter sse = SseHelper.createSse(1000L * 60 * 30, EndpointContext.getProcessData().getRequestId());
            adaptor.streamCompletion(request, ctx.url, property, StreamCallbackProvider.provide(sse, processData, EndpointContext.getApikey(), logger, safetyCheckService, property));
            return sse;
        }

        CompletionResponse response = adaptor.completion(request, ctx.url, property);
        Object responseRiskData = safetyCheckService.safetyCheck(SafetyCheckRequest.Chat.convertFrom(response, EndpointContext.getProcessData(), EndpointContext.getApikey()), isMock);
        response.setSensitives(responseRiskData);
        response.setRequestRiskData(requestRiskData);
        return response;
    }

    private void fillMockProperty(CompletionProperty property) {
        Map<String, String> requestInfo = BellaContext.getHeaders();
        String functionCallSimulate = requestInfo.get("X-BELLA-FUNCTION-SIMULATE");
        String mergeReasoningContent = requestInfo.get("X-BELLA-MERGE-REASONING");
        String splitReasoningFromContent = requestInfo.get("X-BELLA-SPLIT-REASONING");
        property.setFunctionCallSimulate("true".equals(functionCallSimulate) || property.isFunctionCallSimulate());
        property.setMergeReasoningContent("true".equals(mergeReasoningContent) || property.isMergeReasoningContent());
        property.setSplitReasoningFromContent("true".equals(splitReasoningFromContent) || property.isSplitReasoningFromContent());
    }

    private CompletionAdaptor<?> decorateAdaptor(CompletionAdaptor<?> adaptor, CompletionProperty property, EndpointProcessData processData) {
        if(StringUtils.isNotBlank(property.getQueueName())) {
            if(adaptor instanceof CompletionAdaptorDelegator) {
                adaptor = new QueueAdaptor<>((CompletionAdaptorDelegator<?>) adaptor, queueClient, processData);
            } else {
                throw new IllegalStateException(adaptor.getClass().getSimpleName() + "不支持请求代理");
            }
        }
        if(property.isFunctionCallSimulate()) {
            adaptor = new ToolCallSimulator<>(adaptor, processData);
        }
        return adaptor;
    }

    /**
     * Common initialization for both normal and direct mode
     */
    private static class ChannelContext {
        String url;
        CompletionAdaptor adaptor;
        CompletionProperty property;
    }

    private ChannelContext initializeChannel(String endpoint, String model, boolean isDirectMode) {
        // Set endpoint data
        endpointDataService.setEndpointData(endpoint, model, null);

        // Route to channel (direct mode skips availability checks)
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), false, isDirectMode);
        endpointDataService.setChannel(channel);

        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();

        // Get adaptor and property
        CompletionAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, CompletionAdaptor.class);
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());

        EndpointContext.setEncodingType(property.getEncodingType());

        ChannelContext ctx = new ChannelContext();
        ctx.url = url;
        ctx.adaptor = adaptor;
        ctx.property = property;
        return ctx;
    }

    /**
     * Process request in direct mode with DirectPassthroughAdaptor:
     * 1. Use X-BELLA-MODEL header for model routing
     * 2. Use X-BELLA-PROTOCOL header to distinguish HTTP vs SSE
     * 3. Skip availability checks, rate limiting, safety checks
     * 4. Direct transparent passthrough - responses written immediately
     * 5. Async processing for logging, metrics
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object processDirectModeRequest(String endpoint, HttpServletRequest httpRequest, HttpServletResponse httpResponse) throws IOException {
        String model = BellaContext.getDirectModel();
        ChannelContext ctx = initializeChannel(endpoint, model, true);

        // Direct mode requires CompletionAdaptorDelegator
        if (!(ctx.adaptor instanceof CompletionAdaptorDelegator)) {
            throw new IllegalStateException("Direct mode requires CompletionAdaptorDelegator, got: " + ctx.adaptor.getClass().getSimpleName());
        }
        CompletionAdaptorDelegator delegator = (CompletionAdaptorDelegator) ctx.adaptor;

        EndpointProcessData processData = EndpointContext.getProcessData();

        // Create DirectPassthroughAdaptor with HttpServletResponse
        // The adaptor will automatically detect stream vs non-stream based on response Content-Type
        CompletionAdaptor adaptor = new DirectPassthroughAdaptor(delegator, httpRequest.getInputStream(), ctx.property, httpResponse, logger);
        adaptor.completion(null, ctx.url, ctx.property);
        return null; // Response already written directly
    }
}
