package com.ke.bella.openapi.endpoints;

import java.util.List;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.endpoints.async.AsyncExecutor;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.RouteAffinityKeyGenerator;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.callback.StreamCallbackProvider;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.message.MessageAdaptor;
import com.ke.bella.openapi.protocol.message.MessageDelegator;
import com.ke.bella.openapi.protocol.message.MessageDelegatorAdaptor;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import com.ke.bella.openapi.protocol.message.MessageToChatQueueAdaptor;
import com.ke.bella.openapi.protocol.message.QueueMessageAdaptor;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.safety.SafetyCheckHelper;
import com.ke.bella.openapi.safety.SafetyCheckRequest;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.SseHelper;
import com.ke.bella.queue.QueueClient;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
@Slf4j
public class MessageController {
    @Autowired
    private ChannelRouter router;
    @Autowired
    private RouteAffinityKeyGenerator routeAffinityKeyGenerator;
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
    @Autowired
    private QueueClient queueClient;
    @Autowired
    private AsyncExecutor asyncExecutor;

    @PostMapping
    public Object message(@RequestBody MessageRequest request) {
        String endpoint = EndpointContext.getRequest().getRequestURI();
        String model = request.getModel();
        endpointDataService.setEndpointData(endpoint, model, request);
        if(Boolean.TRUE.equals(request.getStream())) {
            return processMessage(endpoint, model, request);
        }
        return asyncExecutor.submit(EndpointContext.getRequest(), () -> processMessage(endpoint, model, request));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object processMessage(String endpoint, String model, MessageRequest request) {
        boolean isMock = EndpointContext.getProcessData().isMock();
        String affinityKey = routeAffinityKeyGenerator.forMessages(endpoint, model, request);
        EndpointProcessData processData = EndpointContext.getProcessData();
        List<ChannelDB> channels = router.routeCandidates(endpoint, model, EndpointContext.getApikey(), isMock, false, affinityKey);
        SseEmitter sse = Boolean.TRUE.equals(request.getStream()) ? SseHelper.createSse(1000L * 60 * 30, processData.getRequestId()) : null;
        BellaException lastRateLimitException = null;
        boolean concurrentIncremented = false;

        for (ChannelDB channel : channels) {
            try {
                MessageChannelContext ctx = initializeChannel(endpoint, channel);
                if(!concurrentIncremented && !processData.isPrivate()) {
                    limiterManager.incrementConcurrentCount(processData.getAkCode(), model);
                    concurrentIncremented = true;
                }
                return processMessageWithChannel(request, channel, ctx, sse, affinityKey);
            } catch (Exception e) {
                BellaException bellaException = BellaException.fromException(e);
                if(Integer.valueOf(429).equals(bellaException.getHttpCode())) {
                    lastRateLimitException = bellaException;
                    log.warn("当前渠道返回429，即将进行重试: endpoint={}, model={}, channelCode={}", endpoint, model, channel.getChannelCode());
                } else {
                    throw bellaException;
                }
            }
        }

        if(lastRateLimitException != null) {
            throw lastRateLimitException;
        }
        throw new BellaException.RateLimitException("渠道当前负载过高，请稍后重试");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private MessageChannelContext initializeChannel(String endpoint, ChannelDB channel) {
        endpointDataService.setChannel(channel);
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
        if(ChannelRouter.isQueueChannel(channel)) {
            if(adaptor instanceof MessageDelegatorAdaptor && StringUtils.isBlank(property.getMessageEndpointUrl())) {
                adaptor = new MessageToChatQueueAdaptor<>((MessageDelegatorAdaptor) adaptor, queueClient, processData);
            } else if(adaptor instanceof MessageDelegator) {
                adaptor = new QueueMessageAdaptor<>((MessageDelegator) adaptor, queueClient, processData);
            } else {
                throw new IllegalStateException(adaptor.getClass().getSimpleName() + "不支持请求代理");
            }
        }
        MessageChannelContext ctx = new MessageChannelContext();
        ctx.url = url;
        ctx.adaptor = adaptor;
        ctx.property = property;
        ctx.safetyService = SafetyCheckHelper.createDelegator(safetyCheckService, property.getSafetyCheckMode());
        return ctx;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Object processMessageWithChannel(MessageRequest request, ChannelDB channel, MessageChannelContext ctx, SseEmitter sse, String affinityKey) {
        boolean isMock = EndpointContext.getProcessData().isMock();
        EndpointProcessData processData = EndpointContext.getProcessData();
        MessageRequest currentRequest = request.copyRequest();
        ISafetyCheckService<SafetyCheckRequest.Chat> chatSafetyCheckService = ctx.safetyService;
        chatSafetyCheckService.safetyCheck(SafetyCheckRequest.Chat.convertFrom(currentRequest, processData, EndpointContext.getApikey()), isMock);

        if(Boolean.TRUE.equals(currentRequest.getStream())) {
            ctx.adaptor.streamMessages(currentRequest, ctx.url, ctx.property,
                    StreamCallbackProvider.provideForMessage(sse, processData, EndpointContext.getApikey(), logger, chatSafetyCheckService, ctx.property));
            cacheAffinity(affinityKey, channel, isMock);
            return sse;
        }

        MessageResponse response = ctx.adaptor.createMessages(currentRequest, ctx.url, ctx.property);
        if(response.getError() != null && Integer.valueOf(429).equals(response.getError().getHttpCode())) {
            throw new BellaException.RateLimitException(response.getError().getMessage());
        }

        chatSafetyCheckService.safetyCheck(SafetyCheckRequest.Chat.convertFrom(response, processData, EndpointContext.getApikey()), isMock);
        cacheAffinity(affinityKey, channel, isMock);
        return response;
    }

    private void cacheAffinity(String affinityKey, ChannelDB channel, boolean isMock) {
        if(!isMock && StringUtils.isNotBlank(affinityKey)) {
            router.setCachedAffinityChannel(affinityKey, channel);
        }
    }

    private static class MessageChannelContext {
        String url;
        MessageAdaptor adaptor;
        CompletionProperty property;
        ISafetyCheckService<SafetyCheckRequest.Chat> safetyService;
    }
}
