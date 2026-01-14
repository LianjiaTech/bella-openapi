package com.ke.bella.openapi.endpoints;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.callback.StreamCallbackProvider;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.message.MessageAdaptor;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.SseHelper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@EndpointAPI
@RestController
@RequestMapping("/v1/messages")
@Tag(name = "messages")
public class MessageController {
    @Autowired
    private ChannelRouter router;
    @Autowired
    private AdaptorManager adaptorManager;
    @Autowired
    private LimiterManager limiterManager;
    @Autowired
    private EndpointDataService endpointDataService;
    @Autowired
    private EndpointLogger logger;
    @Autowired
    private ISafetyCheckService.IChatSafetyCheckService safetyCheckService;

    @PostMapping
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object message(@RequestBody MessageRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request);
        boolean isMock = EndpointContext.getProcessData().isMock();
        ChannelDB channel = router.route(endpoint, model, EndpointContext.getApikey(), isMock);
        endpointDataService.setChannel(channel);
        if(!EndpointContext.getProcessData().isPrivate()) {
            limiterManager.incrementConcurrentCount(EndpointContext.getProcessData().getAkCode(), model);
        }
        EndpointProcessData processData = EndpointContext.getProcessData();
        String protocol = processData.getProtocol();
        String url = processData.getForwardUrl();
        String channelInfo = channel.getChannelInfo();
        MessageAdaptor adaptor = adaptorManager.getProtocolAdaptor(endpoint, protocol, MessageAdaptor.class);
        if(adaptor == null) {
            throw new BizParamCheckException("Unsupported protocol.");
        }
        CompletionProperty property = (CompletionProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        EndpointContext.setEncodingType(property.getEncodingType());
        if(Boolean.TRUE.equals(request.getStream())) {
            SseEmitter sse = SseHelper.createSse(1000L * 60 * 30, EndpointContext.getProcessData().getRequestId());
            adaptor.streamMessages(request, url, property,
                    StreamCallbackProvider.provideForMessage(sse, processData, EndpointContext.getApikey(), logger, safetyCheckService, property));
            return sse;
        }
        return adaptor.createMessages(request, url, property);
    }
}
