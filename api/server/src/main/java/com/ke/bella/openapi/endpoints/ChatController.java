package com.ke.bella.openapi.endpoints;

import java.util.Map;

import com.ke.bella.openapi.protocol.completion.CompletionAdaptorDelegator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.QueueAdaptor;
import com.ke.bella.openapi.protocol.completion.ToolCallSimulator;
import com.ke.bella.openapi.protocol.completion.callback.StreamCallbackProvider;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.service.JobQueueService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.SseHelper;

import io.swagger.v3.oas.annotations.tags.Tag;

@EndpointAPI
@RestController
@RequestMapping("/v1/chat")
@Tag(name = "chat")
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
    private JobQueueService jobQueueService;
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @PostMapping("/completions")
    public Object completion(@RequestBody CompletionRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        EndpointContext.setEndpointData(endpoint, model, request);
        boolean isMock = EndpointContext.getProcessData().isMock();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), isMock); // This now handles multiple models
        EndpointContext.setEndpointData(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        Object requestRiskData = safetyCheckService.safetyCheck(SafetyCheckRequest.Chat.convertFrom(request,
                    EndpointContext.getProcessData(), EndpointContext.getApikey()), isMock);
        EndpointContext.getProcessData().setRequestRiskData(requestRiskData);
        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        CompletionAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, CompletionAdaptor.class);
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        if(isMock) {
            fillMockProperty(property);
        }
        adaptor = decorateAdaptor(adaptor, property, processData);

        EndpointContext.setEncodingType(property.getEncodingType());
        if(request.isStream()) {
            SseEmitter sse = SseHelper.createSse(1000L * 60 * 5, EndpointContext.getProcessData().getRequestId());
            adaptor.streamCompletion(request, url, property, StreamCallbackProvider.provide(sse, processData, EndpointContext.getApikey(), logger, safetyCheckService, property));
            return sse;
        }

        CompletionResponse response = adaptor.completion(request, url, property);
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
                adaptor = new QueueAdaptor<>((CompletionAdaptorDelegator<?>)adaptor, jobQueueService, jobQueueService, processData);
            } else {
                throw new IllegalStateException(adaptor.getClass().getSimpleName() + "不支持请求代理");
            }
        }
        if(property.isFunctionCallSimulate()) {
            adaptor = new ToolCallSimulator<>(adaptor, processData);
        }
        return adaptor;
    }
}
