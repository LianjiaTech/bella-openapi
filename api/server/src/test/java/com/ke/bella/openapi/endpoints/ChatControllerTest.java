package com.ke.bella.openapi.endpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

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
import com.ke.bella.openapi.protocol.RouteAffinityKeyGenerator;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.safety.ISafetyCheckService;
import com.ke.bella.openapi.service.EndpointDataService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.QueueMode;

public class ChatControllerTest {
    private static final String ENDPOINT = "/v1/chat/completions";
    private static final String MODEL = "gpt-test";
    private static final String AFFINITY_KEY = "affinity-key";

    private ChatController controller;
    private RetryAdaptor adaptor;
    private TestChannelRouter router;

    @Before
    public void setUp() {
        controller = new ChatController();
        adaptor = new RetryAdaptor();
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
    public void processCompletionRequest_retriesNextChannelWhenCompletionThrowsRateLimit() {
        ChannelDB rateLimited = channel("ch-rate-limited", "url-429-http");
        ChannelDB success = channel("ch-success", "url-success");
        success.setQueueName("disabled-queue");
        success.setQueueMode(QueueMode.NONE.getCode().byteValue());
        CompletionRequest request = CompletionRequest.builder().model(MODEL).build();
        router.channels = Arrays.asList(rateLimited, success);

        CompletionResponse response = ReflectionTestUtils.invokeMethod(controller, "processCompletionRequest", ENDPOINT, MODEL, request);

        assertNotNull(response);
        assertEquals("url-success", response.getId());
        assertEquals(Arrays.asList("url-429-http", "url-success"), adaptor.completionUrls);
        assertEquals(AFFINITY_KEY, router.cachedAffinityKey);
        assertEquals(success, router.cachedChannel);
    }

    @Test
    public void processCompletionRequest_retriesNextChannelWhenStreamCompletionThrowsRateLimit() {
        ChannelDB rateLimited = channel("ch-rate-limited", "url-429-stream");
        ChannelDB success = channel("ch-success", "url-success-stream");
        CompletionRequest request = CompletionRequest.builder().model(MODEL).stream(true).build();
        router.channels = Arrays.asList(rateLimited, success);

        SseEmitter response = ReflectionTestUtils.invokeMethod(controller, "processCompletionRequest", ENDPOINT, MODEL, request);

        assertNotNull(response);
        assertEquals(Arrays.asList("url-429-stream", "url-success-stream"), adaptor.streamUrls);
        assertEquals(AFFINITY_KEY, router.cachedAffinityKey);
        assertEquals(success, router.cachedChannel);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void processCompletionRequest_preservesExtensionMaps() {
        ChannelDB success = channel("ch-success", "url-success");
        CompletionRequest request = CompletionRequest.builder().model(MODEL).build();
        Map<String, Object> chatTemplateKwargs = new HashMap<>();
        chatTemplateKwargs.put("temperature_scale", 0.8);
        Map<String, Object> extraBody = new HashMap<>();
        extraBody.put("custom_parameter", "custom-value");
        extraBody.put("chat_template_kwargs", chatTemplateKwargs);
        Map<String, Object> realExtraBody = new HashMap<>();
        realExtraBody.put("nested", new HashMap<>(chatTemplateKwargs));
        request.setExtra_body(extraBody);
        request.setRealExtraBody(realExtraBody);
        router.channels = Arrays.asList(success);

        ReflectionTestUtils.invokeMethod(controller, "processCompletionRequest", ENDPOINT, MODEL, request);

        CompletionRequest captured = adaptor.requests.get(0);
        assertEquals("custom-value", captured.getExtra_body().get("custom_parameter"));
        assertEquals(0.8, ((Map<String, Object>) captured.getExtra_body().get("chat_template_kwargs")).get("temperature_scale"));
        assertEquals(0.8,
                ((Map<String, Object>) ((Map<String, Object>) captured.getRealExtraBody()).get("nested")).get("temperature_scale"));
        assertNotSame(extraBody, captured.getExtra_body());
        assertNotSame(chatTemplateKwargs, captured.getExtra_body().get("chat_template_kwargs"));
        assertNotSame(realExtraBody, captured.getRealExtraBody());
    }

    @Test
    public void processCompletionRequest_mockDoesNotCacheAffinity() {
        ChannelDB mock = channel("ch-mock", "url-mock");
        CompletionRequest request = CompletionRequest.builder().model(MODEL).build();
        router.channels = Arrays.asList(mock);
        EndpointContext.getProcessData().setMock(true);

        CompletionResponse response = ReflectionTestUtils.invokeMethod(controller, "processCompletionRequest", ENDPOINT, MODEL, request);

        assertNotNull(response);
        assertEquals("url-mock", response.getId());
        assertEquals(null, router.cachedAffinityKey);
        assertEquals(null, router.cachedChannel);
    }

    private ChannelDB channel(String channelCode, String url) {
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode(channelCode);
        channel.setUrl(url);
        channel.setProtocol(RetryAdaptor.class.getSimpleName());
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
        public String forChat(String endpoint, String model, CompletionRequest request) {
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

    private static class RetryAdaptor implements CompletionAdaptor<CompletionProperty> {
        private final List<String> completionUrls = new ArrayList<>();
        private final List<String> streamUrls = new ArrayList<>();
        private final List<CompletionRequest> requests = new ArrayList<>();

        @Override
        public CompletionResponse completion(CompletionRequest request, String url, CompletionProperty property) {
            requests.add(request);
            completionUrls.add(url);
            if("url-429-http".equals(url)) {
                throw new BellaException.ChannelException(429, "");
            }
            CompletionResponse response = new CompletionResponse();
            response.setId(url);
            return response;
        }

        @Override
        public void streamCompletion(CompletionRequest request, String url, CompletionProperty property,
                Callbacks.StreamCompletionCallback callback) {
            requests.add(request);
            streamUrls.add(url);
            if("url-429-stream".equals(url)) {
                throw new BellaException.RateLimitException("busy");
            }
        }

        @Override
        public String endpoint() {
            return ENDPOINT;
        }

        @Override
        public String getDescription() {
            return "retry test adaptor";
        }

        @Override
        public Class<?> getPropertyClass() {
            return CompletionProperty.class;
        }
    }
}
