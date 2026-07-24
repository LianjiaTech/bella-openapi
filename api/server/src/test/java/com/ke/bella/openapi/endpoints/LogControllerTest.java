package com.ke.bella.openapi.endpoints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.service.ApikeyService;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@RunWith(MockitoJUnitRunner.class)
public class LogControllerTest {
    private LogController controller;
    private Logger logger;
    private Level oldLevel;
    private ListAppender<ILoggingEvent> appender;

    @Mock
    private ApikeyService apikeyService;

    @Before
    public void setUp() {
        controller = new LogController();
        ReflectionTestUtils.setField(controller, "apikeyService", apikeyService);

        logger = (Logger) LoggerFactory.getLogger(LogController.class);
        oldLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(oldLevel);
        appender.stop();
    }

    @Test
    public void record_apikeyNotFound_logsContextWarnAndThrowsBizParamCheckException() {
        EndpointProcessData processData = EndpointProcessData.builder()
                .akSha("ak-sha")
                .bellaTraceId("trace-id")
                .endpoint("/v1/chat/completions")
                .build();
        when(apikeyService.queryBySha("ak-sha", true)).thenReturn(null);

        try {
            controller.record(processData);
            fail("Expected BizParamCheckException");
        } catch (BizParamCheckException e) {
            assertEquals("用户的Apikey不存在", e.getMessage());
        }

        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size());
        assertEquals(Level.WARN, events.get(0).getLevel());
        assertEquals("用户的Apikey不存在, akSha=ak-sha, bellaTraceId=trace-id, endpoint=/v1/chat/completions",
                events.get(0).getFormattedMessage());
        assertNull(events.get(0).getThrowableProxy());
    }
}
