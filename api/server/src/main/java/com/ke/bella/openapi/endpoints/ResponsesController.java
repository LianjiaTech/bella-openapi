package com.ke.bella.openapi.endpoints;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.endpoints.async.AsyncExecutor;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.RouteAffinityKeyGenerator;
import com.ke.bella.openapi.protocol.completion.ResponsesAdaptor;
import com.ke.bella.openapi.protocol.completion.ResponsesApiProperty;
import com.ke.bella.openapi.protocol.completion.ResponsesApiRequest;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.completion.callback.ResponsesApiSseCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckHelper;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.SseHelper;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@EndpointAPI
@RestController
@RequestMapping("/v1/responses")
@Tag(name = "responses")
@Slf4j
public class ResponsesController {

    private static final long SSE_TIMEOUT_MS = 1000L * 60 * 30;

    @Autowired
    private ChannelRouter router;

    @Autowired
    private RouteAffinityKeyGenerator routeAffinityKeyGenerator;

    @Autowired
    private AdaptorManager adaptorManager;

    @Autowired
    private EndpointDataService endpointDataService;

    @Autowired
    private EndpointLogger logger;

    @Autowired
    private AsyncExecutor asyncExecutor;

    @Autowired
    private ISafetyCheckService.IChatSafetyCheckService safetyCheckService;

    @PostMapping
    public Object createResponse(@org.springframework.web.bind.annotation.RequestBody ResponsesApiRequest request,
            HttpServletRequest httpRequest) {
        String endpoint = httpRequest.getRequestURI();

        String model = request.getModel();
        if(StringUtils.isBlank(model)) {
            throw new BizParamCheckException("model is required");
        }
        endpointDataService.setEndpointData(endpoint, model, request);

        if(Boolean.TRUE.equals(request.getStream())) {
            return processCreateResponse(endpoint, model, request);
        }

        return asyncExecutor.submit(httpRequest, () -> processCreateResponse(endpoint, model, request));
    }

    @SuppressWarnings("unchecked")
    private Object processCreateResponse(String endpoint, String model, ResponsesApiRequest request) {
        String channelCode = getChannelCode();

        String affinityKey = routeAffinityKeyGenerator.forResponses(endpoint, model, request);
        ChannelDB channel = routeToChannel(endpoint, model, channelCode, affinityKey);
        endpointDataService.setChannel(channel);

        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();

        ResponsesAdaptor responsesAdaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, ResponsesAdaptor.class);
        if(responsesAdaptor == null) {
            throw new BizParamCheckException("Unsupported protocol: " + protocol);
        }

        ResponsesApiProperty property = (ResponsesApiProperty) JacksonUtils.deserialize(channelInfo, responsesAdaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        // 创建 per-request 的安全检查 delegator
        ISafetyCheckService<SafetyCheckRequest.Chat> chatSafetyCheckService =
                SafetyCheckHelper.createDelegator(safetyCheckService, property.getSafetyCheckMode());

        // 输入安全检查
        chatSafetyCheckService.safetyCheck(SafetyCheckRequest.Chat.convertFrom(request, processData, EndpointContext.getApikey()), false);

        if(Boolean.TRUE.equals(request.getStream())) {
            SseEmitter sse = SseHelper.createSse(SSE_TIMEOUT_MS, processData.getRequestId());
            ResponsesApiSseCallback callback = new ResponsesApiSseCallback(
                    sse, processData, EndpointContext.getApikey(), logger, chatSafetyCheckService);
            ResponsesAdaptor<ResponsesApiProperty> adaptor = responsesAdaptor;
            adaptor.streamResponseAsync(request, url, property, callback);
            return sse;
        }

        ResponsesAdaptor<ResponsesApiProperty> adaptor = responsesAdaptor;
        ResponsesApiResponse response = adaptor.createResponse(request, url, property);

        // 输出安全检查
        chatSafetyCheckService.safetyCheck(SafetyCheckRequest.Chat.convertFrom(response, processData, EndpointContext.getApikey()), false);

        response.set_bella_response(ResponsesApiResponse.BellaResponse.builder()
                .channel_code(channel.getChannelCode())
                .build());
        response.setCreated(DateTimeUtils.getCurrentSeconds());

        return response;
    }

    @GetMapping("/{response_id}")
    public ResponsesApiResponse getResponse(
            @PathVariable("response_id") String responseId) {

        if(StringUtils.isBlank(responseId)) {
            throw new BizParamCheckException("response_id is required");
        }

        String channelCode = getChannelCode();

        if(StringUtils.isBlank(channelCode)) {
            throw new BizParamCheckException("channel_code is required for query");
        }

        ChannelDB channel = router.route(channelCode);

        EndpointProcessData processData = EndpointContext.getProcessData();
        processData.setChannelCode(channel.getChannelCode());
        processData.setProtocol(channel.getProtocol());
        processData.setForwardUrl(channel.getUrl());

        String endpoint = "/v1/responses";
        String protocol = channel.getProtocol();
        String url = channel.getUrl();
        String channelInfo = channel.getChannelInfo();

        ResponsesAdaptor responsesAdaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, ResponsesAdaptor.class);
        if(responsesAdaptor == null) {
            throw new BizParamCheckException("Unsupported protocol: " + protocol);
        }

        ResponsesApiProperty property = (ResponsesApiProperty) JacksonUtils.deserialize(channelInfo, responsesAdaptor.getPropertyClass());

        ResponsesAdaptor<ResponsesApiProperty> adaptor = responsesAdaptor;
        ResponsesApiResponse response = adaptor.getResponse(responseId, url, property);

        response.set_bella_response(ResponsesApiResponse.BellaResponse.builder()
                .channel_code(channel.getChannelCode())
                .build());

        return response;
    }

    private String getChannelCode() {
        return BellaContext.getHeaders().get("X-BELLA-CHANNEL");
    }

    private ChannelDB routeToChannel(String endpoint, String model, String channelCode, String affinityKey) {
        if(StringUtils.isNotBlank(channelCode)) {
            ChannelDB channel = router.route(channelCode);
            if(channel == null) {
                throw new BizParamCheckException("channel_code not found: " + channelCode);
            }
            return channel;
        }

        return router.route(endpoint, model, EndpointContext.getApikey(), false, affinityKey);
    }

}
