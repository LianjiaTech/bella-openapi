package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class MultiModelSupportTest {

    @Mock
    private ChannelService channelService;
    
    @Mock
    private ModelService modelService;
    
    @Mock
    private MetricsManager metricsManager;
    
    @Mock
    private LimiterManager limiterManager;
    
    @InjectMocks
    private ChannelRouter channelRouter;
    
    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(channelRouter, "maxModelsPerRequest", 3);
    }
    
    @Test
    public void testMultipleModelsSupport() {
        // Mock data
        ApikeyInfo apikey = new ApikeyInfo();
        apikey.setSafetyLevel((byte) 30);
        apikey.setOwnerType("user");
        apikey.setOwnerCode("test-user");
        
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode("test-channel");
        channel.setDataDestination("MAINLAND");
        channel.setPriority("HIGH");
        channel.setVisibility("PUBLIC");
        
        // Mock service responses
        when(modelService.fetchTerminalModelName(anyString())).thenReturn("gpt-3.5-turbo");
        when(channelService.listActives(anyString(), anyString())).thenReturn(Arrays.asList(channel));
        when(metricsManager.getAllUnavailableChannels(anyList())).thenReturn(new HashSet<>());
        
        // Test with multiple models
        ChannelDB result = channelRouter.route("/v1/chat/completions", "gpt-3.5-turbo,gpt-4", apikey, false);
        assertNotNull(result);
        assertEquals("test-channel", result.getChannelCode());
    }
}
