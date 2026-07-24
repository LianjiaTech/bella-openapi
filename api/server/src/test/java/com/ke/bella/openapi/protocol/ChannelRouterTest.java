package com.ke.bella.openapi.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.QueueMode;

@RunWith(MockitoJUnitRunner.class)
public class ChannelRouterTest {
    private static final String ENDPOINT = "/v1/chat/completions";

    private ChannelRouter router;

    @Mock
    private ChannelService channelService;
    @Mock
    private ModelService modelService;
    @Mock
    private ApikeyInfo apikeyInfo;
    @Mock
    private MetricsManager metricsManager;
    @Mock
    private RedissonClient redisson;
    @Mock
    private RBucket<String> bucket;

    @Before
    public void setUp() {
        router = new ChannelRouter();
        ReflectionTestUtils.setField(router, "channelService", channelService);
        ReflectionTestUtils.setField(router, "modelService", modelService);
        ReflectionTestUtils.setField(router, "metricsManager", metricsManager);
        ReflectionTestUtils.setField(router, "redisson", redisson);
        AdaptorManager.getInstance().register(ENDPOINT, new TestAdaptor());
    }

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void route_noActiveChannelForModel_includesRequestedModelInMessage() {
        String requestedModel = "missing-model";
        when(modelService.fetchTerminalModelName(requestedModel)).thenReturn("terminal-model");
        when(channelService.listActives(EntityConstants.MODEL, "terminal-model")).thenReturn(Collections.emptyList());

        try {
            router.route(ENDPOINT, requestedModel, apikeyInfo, false);
            fail("Expected BizParamCheckException");
        } catch (BizParamCheckException e) {
            assertTrue(e.getMessage().contains("没有可用渠道"));
            assertTrue(e.getMessage().contains("model: " + requestedModel));
            assertFalse(e.getMessage().contains("terminal-model"));
            assertEquals(EndpointProcessData.FAILURE_STAGE_ROUTE, EndpointContext.getProcessData().getFailureStage());
        }
    }

    @Test
    public void route_inactiveLinkedModel_doesNotQueryTerminalChannels() {
        String requestedModel = "inactive-alias";
        when(modelService.fetchTerminalModelName(requestedModel)).thenThrow(new BizParamCheckException("模型不可用"));

        try {
            router.route(ENDPOINT, requestedModel, apikeyInfo, false);
            fail("Expected BizParamCheckException");
        } catch (BizParamCheckException e) {
            assertEquals("模型不可用", e.getMessage());
            verify(channelService, never()).listActives(eq(EntityConstants.MODEL), anyString());
        }
    }

    @Test
    public void routeQueueMode_noActiveChannelForModel_includesRequestedModelInMessage() {
        String requestedModel = "queue-missing-model";
        when(modelService.fetchTerminalModelName(requestedModel)).thenReturn("terminal-queue-model");
        when(channelService.listActives(EntityConstants.MODEL, "terminal-queue-model")).thenReturn(Collections.emptyList());

        try {
            router.route(ENDPOINT, requestedModel, apikeyInfo, 1);
            fail("Expected BizParamCheckException");
        } catch (BizParamCheckException e) {
            assertTrue(e.getMessage().contains("没有可用通道"));
            assertTrue(e.getMessage().contains("model: " + requestedModel));
            assertFalse(e.getMessage().contains("terminal-queue-model"));
        }
    }

    @Test
    public void route_noModelAndNoActiveEndpoint_keepsGenericNoChannelMessage() {
        when(channelService.listActives(EntityConstants.ENDPOINT, ENDPOINT)).thenReturn(Collections.emptyList());

        try {
            router.route(ENDPOINT, null, apikeyInfo, false);
            fail("Expected BizParamCheckException");
        } catch (BizParamCheckException e) {
            assertEquals("没有可用渠道", e.getMessage());
        }
    }

    @Test
    public void route_directModeCanSelectQueueChannelWithoutCapacityProbe() {
        ChannelDB queue = channel("ch-queue", EntityConstants.HIGH);
        queue.setQueueName("queue-a");
        queue.setQueueMode(QueueMode.ROUTE.getCode().byteValue());
        when(channelService.listActives(EntityConstants.ENDPOINT, ENDPOINT)).thenReturn(Arrays.asList(queue));
        when(apikeyInfo.getSafetyLevel()).thenReturn((byte) 40);

        ChannelDB selected = router.route(ENDPOINT, null, apikeyInfo, false, true);

        assertEquals("ch-queue", selected.getChannelCode());
    }

    @Test
    public void routeCandidates_returnsCompletePriorityList() {
        ChannelDB publicLow = channel("ch-public-low", EntityConstants.LOW);
        ChannelDB publicHigh = channel("ch-public-high", EntityConstants.HIGH);
        ChannelDB publicNormal = channel("ch-public-normal", EntityConstants.NORMAL);
        when(channelService.listActives(EntityConstants.ENDPOINT, ENDPOINT)).thenReturn(Arrays.asList(publicLow, publicHigh, publicNormal));
        when(apikeyInfo.getSafetyLevel()).thenReturn((byte) 40);
        when(metricsManager.getAllUnavailableChannels(anyList())).thenReturn(Collections.emptySet());

        List<ChannelDB> channels = router.routeCandidates(ENDPOINT, null, apikeyInfo, false, false, null);

        assertEquals(3, channels.size());
        assertEquals("ch-public-high", channels.get(0).getChannelCode());
        assertEquals("ch-public-normal", channels.get(1).getChannelCode());
        assertEquals("ch-public-low", channels.get(2).getChannelCode());
    }

    @Test
    public void routeCandidates_cachedChannelPrecedesQueueInSamePriority() {
        String affinityKey = "affinity-key";
        ChannelDB queue = channel("ch-queue", EntityConstants.HIGH);
        queue.setQueueName("queue-a");
        queue.setQueueMode(QueueMode.ROUTE.getCode().byteValue());
        ChannelDB cached = channel("ch-cached", EntityConstants.HIGH);
        when(channelService.listActives(EntityConstants.ENDPOINT, ENDPOINT)).thenReturn(Arrays.asList(queue, cached));
        when(apikeyInfo.getSafetyLevel()).thenReturn((byte) 40);
        when(metricsManager.getAllUnavailableChannels(anyList())).thenReturn(Collections.emptySet());
        when(redisson.<String>getBucket(affinityKey)).thenReturn(bucket);
        when(bucket.get()).thenReturn("ch-cached");

        List<ChannelDB> channels = router.routeCandidates(ENDPOINT, null, apikeyInfo, false, false, affinityKey);

        assertEquals("ch-cached", channels.get(0).getChannelCode());
        assertEquals("ch-queue", channels.get(1).getChannelCode());
    }

    private ChannelDB channel(String channelCode, String priority) {
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode(channelCode);
        channel.setProtocol("TestAdaptor");
        channel.setPriority(priority);
        channel.setVisibility(EntityConstants.PUBLIC);
        channel.setDataDestination(EntityConstants.MAINLAND);
        channel.setChannelInfo("{}");
        return channel;
    }

    private static class TestAdaptor implements IProtocolAdaptor {
        @Override
        public String endpoint() {
            return ENDPOINT;
        }

        @Override
        public String getDescription() {
            return "test";
        }

        @Override
        public Class<?> getPropertyClass() {
            return Object.class;
        }
    }
}
