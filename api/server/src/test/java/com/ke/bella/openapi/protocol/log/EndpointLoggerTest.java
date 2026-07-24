package com.ke.bella.openapi.protocol.log;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.ke.bella.openapi.EndpointProcessData;
import com.lmax.disruptor.RingBuffer;

@RunWith(MockitoJUnitRunner.class)
public class EndpointLoggerTest {

    @Mock
    private RingBuffer<LogEvent> ringBuffer;

    @InjectMocks
    private EndpointLogger endpointLogger;

    private LogEvent sharedEvent;

    @Before
    public void setUp() throws Exception {
        sharedEvent = new LogEvent();
        java.util.List<EndpointLogHandler> handlers = Collections.emptyList();
        java.lang.reflect.Field handlersField = EndpointLogger.class.getDeclaredField("logHandlers");
        handlersField.setAccessible(true);
        handlersField.set(endpointLogger, handlers);
        java.lang.reflect.Field logRepoField = EndpointLogger.class.getDeclaredField("logRepo");
        logRepoField.setAccessible(true);
        logRepoField.set(endpointLogger, "consoleLogRepo");
        endpointLogger.init();
    }

    @Test
    public void testCostOnlyResetOnReuse() {
        when(ringBuffer.next()).thenReturn(0L);
        when(ringBuffer.get(0L)).thenReturn(sharedEvent);

        EndpointProcessData batchLog = EndpointProcessData.builder()
                .batch(true)
                .endpoint("/v1/chat/completions")
                .build();
        endpointLogger.log(batchLog);
        assertTrue(sharedEvent.isCostOnly());

        EndpointProcessData normalLog = EndpointProcessData.builder()
                .batch(false)
                .overrideInnerLog(false)
                .endpoint("/v1/chat/completions")
                .build();
        endpointLogger.log(normalLog);
        assertFalse(sharedEvent.isCostOnly());
    }

    @Test
    public void testCostOnlySetWhenConditionMet() {
        when(ringBuffer.next()).thenReturn(0L);
        when(ringBuffer.get(0L)).thenReturn(sharedEvent);

        EndpointProcessData normalLog = EndpointProcessData.builder()
                .batch(false)
                .overrideInnerLog(false)
                .endpoint("/v1/chat/completions")
                .build();
        endpointLogger.log(normalLog);
        assertFalse(sharedEvent.isCostOnly());

        EndpointProcessData overrideLog = EndpointProcessData.builder()
                .overrideInnerLog(true)
                .endpoint("/v1/chat/completions")
                .build();
        endpointLogger.log(overrideLog);
        assertTrue(sharedEvent.isCostOnly());
    }
}
