package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.EntityConstants;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChannelRouterTest {

    @Mock
    private ChannelService channelService;

    @Mock
    private ModelService modelService;

    @InjectMocks
    private ChannelRouter channelRouter;

    private ApikeyInfo apikeyInfo;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        apikeyInfo = new ApikeyInfo();
        apikeyInfo.setCode("test-ak");
        apikeyInfo.setOwnerType("space");
        apikeyInfo.setOwnerCode("space-001");
        apikeyInfo.setSafetyLevel((byte) 50);
    }

    @Test
    public void testListAvailableChannels_WithEndpoint() {
        String endpoint = "/v1/chat/completions";

        List<ChannelDB> mockChannels = createMockChannels();
        when(channelService.listActives(EntityConstants.ENDPOINT, endpoint))
                .thenReturn(mockChannels);

        List<ChannelDB> result = channelRouter.listAvailableChannels(endpoint, null, apikeyInfo, null);

        assertNotNull(result);
        verify(channelService).listActives(EntityConstants.ENDPOINT, endpoint);
    }

    @Test
    public void testListAvailableChannels_WithModel() {
        String endpoint = "/v1/chat/completions";
        String model = "gpt-4";
        String terminalModel = "gpt-4-terminal";

        List<ChannelDB> mockChannels = createMockChannels();
        when(modelService.fetchTerminalModelName(model)).thenReturn(terminalModel);
        when(channelService.listActives(EntityConstants.MODEL, terminalModel))
                .thenReturn(mockChannels);

        List<ChannelDB> result = channelRouter.listAvailableChannels(endpoint, model, apikeyInfo, null);

        assertNotNull(result);
        verify(modelService).fetchTerminalModelName(model);
        verify(channelService).listActives(EntityConstants.MODEL, terminalModel);
    }

    @Test
    public void testListAvailableChannels_BlankEndpoint() {
        assertThrows(BizParamCheckException.class, () ->
                channelRouter.listAvailableChannels("", null, apikeyInfo, null));

        assertThrows(BizParamCheckException.class, () ->
                channelRouter.listAvailableChannels(null, null, apikeyInfo, null));
    }

    @Test
    public void testListAvailableChannels_NoChannels() {
        String endpoint = "/v1/chat/completions";

        when(channelService.listActives(EntityConstants.ENDPOINT, endpoint))
                .thenReturn(Collections.emptyList());

        List<ChannelDB> result = channelRouter.listAvailableChannels(endpoint, null, apikeyInfo, null);

        assertNull(result);
    }

    @Test
    public void testListAvailableChannels_SortByPriority() {
        String endpoint = "/v1/chat/completions";
        ChannelDB ch1 = createChannel("ch1", EntityConstants.PUBLIC, EntityConstants.LOW);
        ChannelDB ch2 = createChannel("ch2", EntityConstants.PUBLIC, EntityConstants.HIGH);
        ChannelDB ch3 = createChannel("ch3", EntityConstants.PRIVATE, EntityConstants.LOW);
        ChannelDB ch4 = createChannel("ch4", EntityConstants.PRIVATE, EntityConstants.HIGH);
        ChannelDB ch5 = createChannel("ch5", EntityConstants.PUBLIC, EntityConstants.NORMAL);

        List<ChannelDB> mockChannels = Arrays.asList(ch1, ch2, ch3, ch4, ch5);
        when(channelService.listActives(EntityConstants.ENDPOINT, endpoint))
                .thenReturn(mockChannels);

        List<ChannelDB> result = channelRouter.listAvailableChannels(endpoint, null, apikeyInfo, null);

        assertNotNull(result);
        assertEquals(5, result.size());
        assertEquals("ch4", result.get(0).getChannelCode());
        assertEquals("ch3", result.get(1).getChannelCode());
        assertEquals("ch2", result.get(2).getChannelCode());
        assertEquals("ch5", result.get(3).getChannelCode());
        assertEquals("ch1", result.get(4).getChannelCode());
    }

    @Test
    public void testRoute_ReturnsHighestPriority() {
        String endpoint = "/v1/chat/completions";

        ChannelDB ch1 = createChannel("ch1", EntityConstants.PUBLIC, EntityConstants.LOW);
        ChannelDB ch2 = createChannel("ch2", EntityConstants.PUBLIC, EntityConstants.HIGH);
        ChannelDB ch3 = createChannel("ch3", EntityConstants.PRIVATE, EntityConstants.HIGH);

        List<ChannelDB> mockChannels = Arrays.asList(ch1, ch2, ch3);
        when(channelService.listActives(EntityConstants.ENDPOINT, endpoint))
                .thenReturn(mockChannels);

        ChannelDB result = channelRouter.route(endpoint, null, apikeyInfo, null);

        assertNotNull(result);
        assertEquals("ch3", result.getChannelCode());
    }

    @Test
    public void testRoute_NoChannelsAvailable() {
        String endpoint = "/v1/chat/completions";

        when(channelService.listActives(EntityConstants.ENDPOINT, endpoint))
                .thenReturn(Collections.emptyList());

        assertThrows(BizParamCheckException.class, () ->
                channelRouter.route(endpoint, null, apikeyInfo, null));
    }

    @Test
    public void testListAvailableChannels_WithQueueMode() {
        String endpoint = "/v1/chat/completions";
        Integer queueMode = 1;

        List<ChannelDB> mockChannels = createMockChannelsWithQueueMode();
        when(channelService.listActives(EntityConstants.ENDPOINT, endpoint))
                .thenReturn(mockChannels);

        List<ChannelDB> result = channelRouter.listAvailableChannels(endpoint, null, apikeyInfo, queueMode);

        assertNotNull(result);
    }

    private List<ChannelDB> createMockChannels() {
        ChannelDB channel1 = createChannel("ch1", EntityConstants.PUBLIC, EntityConstants.NORMAL);
        ChannelDB channel2 = createChannel("ch2", EntityConstants.PUBLIC, EntityConstants.HIGH);
        return Arrays.asList(channel1, channel2);
    }

    private ChannelDB createChannel(String code, String visibility, String priority) {
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode(code);
        channel.setVisibility(visibility);
        channel.setPriority(priority);
        channel.setProtocol("MockProtocol");
        channel.setQueueMode((byte) 1);
        channel.setOwnerType("space");
        channel.setOwnerCode("space-001");
        channel.setDataDestination("public");
        channel.setStatus("active");
        channel.setUrl("http://test.com");
        return channel;
    }

    private List<ChannelDB> createMockChannelsWithQueueMode() {
        ChannelDB channel1 = createChannel("ch1", EntityConstants.PUBLIC, EntityConstants.NORMAL);
        channel1.setQueueMode((byte) 1);

        ChannelDB channel2 = createChannel("ch2", EntityConstants.PUBLIC, EntityConstants.HIGH);
        channel2.setQueueMode((byte) 2);

        return Arrays.asList(channel1, channel2);
    }
}
