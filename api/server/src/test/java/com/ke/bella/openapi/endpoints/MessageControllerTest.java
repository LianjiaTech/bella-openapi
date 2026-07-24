package com.ke.bella.openapi.endpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private MessageController controller;
    private RetryMessageAdaptor adaptor;
    private TestChannelRouter router;

    @Before
    public void setUp() {
        controller = new MessageController();
        adaptor = new RetryMessageAdaptor();
        router = new TestChannelRouter();
        AdaptorManager.getInstance().register(ENDPOINT, adaptor);

        ReflectionTestUtils.setField(controller, "router", router);
        ReflectionTestUtils.setField(controller, "routeAffinityKeyGenerator", new TestRouteAffinityKeyGenerator());
        ReflectionTestUtils.setField(controller, "adaptorManager", AdaptorManager.getInstance());
        ReflectionTestUtils.setField(controller, "limiterManager", new NoopLimiterManager());
        ReflectionTestUtils.setField(controller, "safetyCheckService",
                (ISafetyCheckService.IChatSafetyCheckService) (request, isMock) -> null);
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
    public void processMessage_queueNameWithNoneModeDoesNotWrapAdaptor() {
        ChannelDB channel = channel("ch-none", "url-none");
        channel.setQueueName("queue-disabled");
        channel.setQueueMode(QueueMode.NONE.getCode().byteValue());
        router.channels = Arrays.asList(channel);

        MessageResponse response = ReflectionTestUtils.invokeMethod(controller, "processMessage", ENDPOINT, MODEL,
                MessageRequest.builder().model(MODEL).build());

        assertEquals("url-none", response.getId());
    }

    private ChannelDB channel(String channelCode, String url) {
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode(channelCode);
        channel.setUrl(url);
        channel.setProtocol(RetryMessageAdaptor.class.getSimpleName());
        channel.setVisibility(EntityConstants.PUBLIC);
        channel.setChannelInfo("{\"safetyCheckMode\":\"skip\"}");
        return channel;
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
            request.setModel("mutated-" + url);
            if("url-stream-429".equals(url)) {
                throw new BellaException.RateLimitException("busy");
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
