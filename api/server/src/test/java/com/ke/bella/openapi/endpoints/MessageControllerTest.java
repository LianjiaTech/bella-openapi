package com.ke.bella.openapi.endpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.ChannelRouter;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.RouteAffinityKeyGenerator;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.message.MessageAdaptor;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.QueueMode;

public class MessageControllerTest {
    private static final String ENDPOINT = "/v1/messages";
    private static final String MODEL = "claude-test";
    private static final String AFFINITY_KEY = "affinity-key";
    private static final String ATTRIBUTION = "x-anthropic-billing-header: cc_version=2.1.153.2cc; cc_entrypoint=sdk-py; cch=2ac08;";

    private MessageController controller;
    private RetryMessageAdaptor adaptor;
    private TestChannelRouter router;
    private List<String> safetyContentTypes;

    @Before
    public void setUp() {
        controller = new MessageController();
        adaptor = new RetryMessageAdaptor();
        router = new TestChannelRouter();
        safetyContentTypes = new ArrayList<>();
        AdaptorManager.getInstance().register(ENDPOINT, adaptor);

        ReflectionTestUtils.setField(controller, "router", router);
        ReflectionTestUtils.setField(controller, "routeAffinityKeyGenerator", new TestRouteAffinityKeyGenerator());
        ReflectionTestUtils.setField(controller, "adaptorManager", AdaptorManager.getInstance());
        ReflectionTestUtils.setField(controller, "limiterManager", new NoopLimiterManager());
        ReflectionTestUtils.setField(controller, "logger", mock(EndpointLogger.class));
        ReflectionTestUtils.setField(controller, "safetyCheckService",
                (ISafetyCheckService.IChatSafetyCheckService) (request, isMock) -> {
                    if(request != null && request.isRequest()) {
                        safetyContentTypes.add(contentType((MessageRequest) request.getOriginalRequest()));
                    }
                    return null;
                });
        ReflectionTestUtils.setField(controller, "endpointDataService", new EndpointDataService());

        EndpointContext.setApikey(ApikeyInfo.builder().apikey("ak").code("ak-code").safetyLevel((byte) 40).build());
    }

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void processMessage_retriesNextChannelWhenResponseIsRateLimited() {
        ChannelDB rateLimited = channel("ch-rate-limited", "url-response-429");
        ChannelDB success = channel("ch-success", "url-success");
        MessageRequest request = MessageRequest.builder().model(MODEL).build();
        router.channels = Arrays.asList(rateLimited, success);

        MessageResponse response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

        assertNotNull(response);
        assertEquals("url-success", response.getId());
        assertEquals(Arrays.asList("url-response-429", "url-success"), adaptor.createUrls);
        assertEquals(MODEL, request.getModel());
        assertNotSame(adaptor.requests.get(0), adaptor.requests.get(1));
        assertEquals(AFFINITY_KEY, router.cachedAffinityKey);
        assertEquals(success, router.cachedChannel);
    }

    @Test
    public void processMessage_retriesNextChannelWhenStreamThrowsRateLimit() {
        ChannelDB rateLimited = channel("ch-rate-limited", "url-stream-429");
        ChannelDB success = channel("ch-success", "url-stream-success");
        MessageRequest request = MessageRequest.builder().model(MODEL).stream(true).build();
        router.channels = Arrays.asList(rateLimited, success);

        SseEmitter response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

        assertNotNull(response);
        assertEquals(Arrays.asList("url-stream-429", "url-stream-success"), adaptor.streamUrls);
        assertNotSame(adaptor.requests.get(0), adaptor.requests.get(1));
        assertEquals(AFFINITY_KEY, router.cachedAffinityKey);
        assertEquals(success, router.cachedChannel);
    }

    @Test
    public void processMessage_replacesInputBlocksBeforeNonStreamAdaptorWithoutReorderingSafetyCheck() {
        ChannelDB success = replacementChannel("ch-success", "url-success");
        MessageRequest request = messageRequest(false);
        router.channels = Arrays.asList(success);

        ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

        MessageRequest processed = adaptor.requests.get(0);
        assertEquals("custom image replacement", contentText(processed));
        assertEquals("image", safetyContentTypes.get(0));
    }

    @Test
    public void processMessage_replacesInputBlocksBeforeStreamAdaptorWithoutReorderingSafetyCheck() {
        ChannelDB success = replacementChannel("ch-success", "url-stream-success");
        MessageRequest request = messageRequest(true);
        router.channels = Arrays.asList(success);

        ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

        MessageRequest processed = adaptor.requests.get(0);
        assertEquals("custom image replacement", contentText(processed));
        assertEquals("image", safetyContentTypes.get(0));
    }

    @Test
    public void processMessage_queueNameWithNoneModeDoesNotWrapAdaptor() {
        ChannelDB channel = channel("ch-none", "url-none");
        channel.setQueueName("queue-disabled");
        channel.setQueueMode(QueueMode.NONE.getCode().byteValue());
        router.channels = Arrays.asList(channel);

        MessageResponse response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL,
                MessageRequest.builder().model(MODEL).build());

        assertEquals("url-none", response.getId());
    }

    @Test
    public void processMessage_stripsAttributionForConfiguredChannelWithoutPollutingFallback() {
        ChannelDB rateLimited = channel("ch-rate-limited", "url-response-429", true);
        ChannelDB success = channel("ch-success", "url-success", false);
        MessageRequest request = MessageRequest.builder().model(MODEL).system(systemBlocks()).build();
        router.channels = Arrays.asList(rateLimited, success);

        MessageResponse response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

        assertEquals("url-success", response.getId());
        assertEquals(Arrays.asList("Stable prompt"), systemTexts(adaptor.requests.get(0)));
        assertEquals(Arrays.asList(ATTRIBUTION, "Stable prompt"), systemTexts(adaptor.requests.get(1)));
        assertEquals(Arrays.asList(ATTRIBUTION, "Stable prompt"), systemTexts(request));
    }

    @Test
    public void processMessage_streamStripsAttributionForConfiguredChannel() {
        ChannelDB channel = channel("ch-stream", "url-stream-success", true);
        MessageRequest request = MessageRequest.builder().model(MODEL).stream(true).system(systemBlocks()).build();
        router.channels = Arrays.asList(channel);

        SseEmitter response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

        assertNotNull(response);
        assertEquals(Arrays.asList("Stable prompt"), systemTexts(adaptor.requests.get(0)));
        assertEquals(Arrays.asList(ATTRIBUTION, "Stable prompt"), systemTexts(request));
    }

    @Test
    public void processMessage_unregistersHeartbeatWhenStreamStartupFailsSynchronously() throws Exception {
        ChannelDB channel = heartbeatChannel("ch-stream-failure", "url-stream-failure");
        MessageRequest request = MessageRequest.builder().model(MODEL).stream(true).build();
        router.channels = Arrays.asList(channel);
        int initialConnectionCount = heartbeatConnectionCount();

        try {
            try {
                ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);
                fail("Expected synchronous stream startup failure");
            } catch (BellaException expected) {
                // The adaptor failed before the SSE emitter was returned to Spring MVC.
            }

            assertNotNull(adaptor.lastStreamCallback);
            assertEquals(initialConnectionCount, heartbeatConnectionCount());
        } finally {
            if(adaptor.lastStreamCallback != null) {
                adaptor.lastStreamCallback.finish();
            }
        }
    }

    @Test
    public void processMessage_registersHeartbeatAfterStreamStartupSucceeds() throws Exception {
        ChannelDB channel = heartbeatChannel("ch-stream-success", "url-stream-success");
        MessageRequest request = MessageRequest.builder().model(MODEL).stream(true).build();
        router.channels = Arrays.asList(channel);
        int initialConnectionCount = heartbeatConnectionCount();

        try {
            SseEmitter response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

            assertNotNull(response);
            assertEquals(initialConnectionCount + 1, heartbeatConnectionCount());
        } finally {
            if(adaptor.lastStreamCallback != null) {
                adaptor.lastStreamCallback.finish();
            }
        }
    }

    @Test
    public void processMessage_doesNotRegisterHeartbeatWhenChannelConfigDisablesIt() throws Exception {
        ChannelDB channel = heartbeatChannel("ch-stream-disabled", "url-stream-success", false, 1);
        MessageRequest request = MessageRequest.builder().model(MODEL).stream(true).build();
        router.channels = Arrays.asList(channel);
        int initialConnectionCount = heartbeatConnectionCount();

        try {
            SseEmitter response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

            assertNotNull(response);
            assertEquals(initialConnectionCount, heartbeatConnectionCount());
        } finally {
            if(adaptor.lastStreamCallback != null) {
                adaptor.lastStreamCallback.finish();
            }
        }
    }

    @Test
    public void processMessage_appliesHeartbeatSettingsFromChannelConfig() {
        ChannelDB channel = heartbeatChannel("ch-stream-custom-interval", "url-stream-success", true, 30);
        MessageRequest request = MessageRequest.builder().model(MODEL).stream(true).build();
        router.channels = Arrays.asList(channel);

        try {
            SseEmitter response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL, request);

            assertNotNull(response);
            assertNotNull(adaptor.lastStreamProperty);
            assertEquals(true, adaptor.lastStreamProperty.isMessageSseHeartbeatEnabled());
            assertEquals(30, adaptor.lastStreamProperty.getMessageSseHeartbeatIntervalSeconds());
        } finally {
            if(adaptor.lastStreamCallback != null) {
                adaptor.lastStreamCallback.finish();
            }
        }
    }

    private ChannelDB channel(String channelCode, String url) {
        return channel(channelCode, url, false);
    }

    private ChannelDB channel(String channelCode, String url, boolean stripClaudeCodeAttribution) {
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode(channelCode);
        channel.setUrl(url);
        channel.setProtocol(RetryMessageAdaptor.class.getSimpleName());
        channel.setVisibility(EntityConstants.PUBLIC);
        channel.setChannelInfo("{\"safetyCheckMode\":\"skip\",\"stripClaudeCodeAttribution\":" + stripClaudeCodeAttribution + "}");
        return channel;
    }

    private ChannelDB replacementChannel(String channelCode, String url) {
        ChannelDB channel = channel(channelCode, url);
        channel.setChannelInfo("{\"safetyCheckMode\":\"sync\",\"inputReplacementEnabled\":true,"
                + "\"inputReplacementTexts\":{\"image\":\"custom image replacement\"}}");
        return channel;
    }

    private ChannelDB heartbeatChannel(String channelCode, String url) {
        return heartbeatChannel(channelCode, url, true, 15);
    }

    private ChannelDB heartbeatChannel(String channelCode, String url, boolean enabled, int intervalSeconds) {
        ChannelDB channel = channel(channelCode, url);
        channel.setChannelInfo("{\"safetyCheckMode\":\"skip\",\"messageSseHeartbeatEnabled\":" + enabled + ","
                + "\"messageSseHeartbeatIntervalSeconds\":" + intervalSeconds + "}");
        return channel;
    }

    private int heartbeatConnectionCount() throws Exception {
        Class<?> managerClass = Class.forName("com.ke.bella.openapi.protocol.message.SseHeartbeatManager");
        Method getInstance = managerClass.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        Object manager = getInstance.invoke(null);
        Method connectionCount = managerClass.getDeclaredMethod("connectionCount");
        connectionCount.setAccessible(true);
        return (Integer) connectionCount.invoke(manager);
    }

    private MessageRequest messageRequest(boolean stream) {
        Map<String, Object> image = new HashMap<>();
        image.put("type", "image");
        image.put("source", "raw-image-data");
        return MessageRequest.builder()
                .model(MODEL)
                .stream(stream)
                .messages(Arrays.asList(MessageRequest.InputMessage.builder()
                        .role("user")
                        .content(Arrays.asList(image))
                        .build()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private String contentText(MessageRequest request) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) request.getMessages().get(0).getContent();
        return String.valueOf(content.get(0).get("text"));
    }

    @SuppressWarnings("unchecked")
    private String contentType(MessageRequest request) {
        List<Map<String, Object>> content = (List<Map<String, Object>>) request.getMessages().get(0).getContent();
        return String.valueOf(content.get(0).get("type"));
    }

    private List<MessageRequest.RequestTextBlock> systemBlocks() {
        return Arrays.asList(systemBlock(ATTRIBUTION), systemBlock("Stable prompt"));
    }

    private MessageRequest.RequestTextBlock systemBlock(String text) {
        return MessageRequest.RequestTextBlock.builder().type("text").text(text).build();
    }

    @SuppressWarnings("unchecked")
    private List<String> systemTexts(MessageRequest request) {
        List<MessageRequest.RequestTextBlock> blocks = (List<MessageRequest.RequestTextBlock>) request.getSystem();
        List<String> texts = new ArrayList<>();
        for (MessageRequest.RequestTextBlock block : blocks) {
            texts.add(block.getText());
        }
        return texts;
    }

    private static class TestChannelRouter extends ChannelRouter {
        private List<ChannelDB> channels;
        private String cachedAffinityKey;
        private ChannelDB cachedChannel;

        @Override
        public List<ChannelDB> routeCandidates(String endpoint, String model, ApikeyInfo apikeyInfo, boolean isMock, boolean isDirectMode,
                String affinityKey) {
            assertEquals(ENDPOINT, endpoint);
            assertEquals(MODEL, model);
            assertEquals(AFFINITY_KEY, affinityKey);
            return channels;
        }

        @Override
        public void setCachedAffinityChannel(String affinityKey, ChannelDB channel) {
            this.cachedAffinityKey = affinityKey;
            this.cachedChannel = channel;
        }
    }

    private static class TestRouteAffinityKeyGenerator extends RouteAffinityKeyGenerator {
        @Override
        public String forMessages(String endpoint, String model, MessageRequest request) {
            assertEquals(ENDPOINT, endpoint);
            assertEquals(MODEL, model);
            return AFFINITY_KEY;
        }
    }

    private static class NoopLimiterManager extends LimiterManager {
        @Override
        public void incrementConcurrentCount(String akCode, String entityCode) {
        }
    }

    private static class RetryMessageAdaptor implements MessageAdaptor<CompletionProperty> {
        private final List<String> createUrls = new ArrayList<>();
        private final List<String> streamUrls = new ArrayList<>();
        private final List<MessageRequest> requests = new ArrayList<>();
        private Callbacks.StreamCompletionCallback lastStreamCallback;
        private CompletionProperty lastStreamProperty;

        @Override
        public MessageResponse createMessages(MessageRequest request, String url, CompletionProperty property) {
            requests.add(request);
            createUrls.add(url);
            request.setModel("mutated-" + url);
            MessageResponse response = new MessageResponse();
            response.setId(url);
            if("url-response-429".equals(url)) {
                response.setError(new OpenapiResponse.OpenapiError("rate_limit", "busy", 429));
            }
            return response;
        }

        @Override
        public void streamMessages(MessageRequest request, String url, CompletionProperty property, Callbacks.StreamCompletionCallback callback) {
            requests.add(request);
            streamUrls.add(url);
            lastStreamCallback = callback;
            lastStreamProperty = property;
            request.setModel("mutated-" + url);
            if("url-stream-429".equals(url)) {
                throw new BellaException.RateLimitException("busy");
            }
            if("url-stream-failure".equals(url)) {
                throw new IllegalStateException("stream startup failed");
            }
        }

        @Override
        public String getDescription() {
            return "message retry test adaptor";
        }

        @Override
        public Class<?> getPropertyClass() {
            return CompletionProperty.class;
        }
    }
}
