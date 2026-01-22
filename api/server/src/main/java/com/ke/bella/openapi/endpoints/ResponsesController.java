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
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.completion.ResponsesAdaptor;
import com.ke.bella.openapi.protocol.completion.ResponsesApiProperty;
import com.ke.bella.openapi.protocol.completion.ResponsesApiRequest;
import com.ke.bella.openapi.protocol.completion.ResponsesApiResponse;
import com.ke.bella.openapi.protocol.completion.callback.ResponsesApiSseCallback;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
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
    private AdaptorManager adaptorManager;

    @Autowired
    private EndpointDataService endpointDataService;

    @Autowired
    private EndpointLogger logger;

    @PostMapping
    public Object createResponse(@org.springframework.web.bind.annotation.RequestBody ResponsesApiRequest request,
            HttpServletRequest httpRequest) {
        String endpoint = httpRequest.getRequestURI();

        String model = request.getModel();
        if(StringUtils.isBlank(model)) {
            throw new BizParamCheckException("model is required");
        }

        endpointDataService.setEndpointData(endpoint, model, request);

        String channelCode = getChannelCode();

        ChannelDB channel = routeToChannel(endpoint, model, channelCode);
        endpointDataService.setChannel(channel);

        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();

        ResponsesAdaptor responsesAdaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, ResponsesAdaptor.class);
        if(responsesAdaptor == null) {
            throw new BizParamCheckException("Unsupported protocol: " + protocol);
        }

        @SuppressWarnings("unchecked")
        ResponsesApiProperty property = (ResponsesApiProperty) JacksonUtils.deserialize(channelInfo, responsesAdaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());

        if(Boolean.TRUE.equals(request.getStream())) {
            SseEmitter sse = SseHelper.createSse(SSE_TIMEOUT_MS, processData.getRequestId());
            ResponsesApiSseCallback callback = new ResponsesApiSseCallback(
                    sse, processData, EndpointContext.getApikey(), logger);
            @SuppressWarnings("unchecked")
            ResponsesAdaptor<ResponsesApiProperty> adaptor = responsesAdaptor;
            adaptor.streamResponseAsync(request, url, property, callback);
            return sse;
        }

        @SuppressWarnings("unchecked")
        ResponsesAdaptor<ResponsesApiProperty> adaptor = responsesAdaptor;
        ResponsesApiResponse response = adaptor.createResponse(request, url, property);

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

    private ChannelDB routeToChannel(String endpoint, String model, String channelCode) {
        if(StringUtils.isNotBlank(channelCode)) {
            ChannelDB channel = router.route(channelCode);
            if(channel == null) {
                throw new BizParamCheckException("channel_code not found: " + channelCode);
            }
            return channel;
        }

        return router.route(endpoint, model, EndpointContext.getApikey(), false);
    }

}
