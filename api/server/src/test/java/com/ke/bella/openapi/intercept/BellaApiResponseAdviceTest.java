package com.ke.bella.openapi.intercept;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.ke.bella.openapi.BellaResponse;
import com.ke.bella.openapi.common.exception.BizParamCheckException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

public class BellaApiResponseAdviceTest {
    private final BellaApiResponseAdvice advice = new BellaApiResponseAdvice();
    private Logger logger;
    private Level oldLevel;
    private ListAppender<ILoggingEvent> appender;

    @Before
    public void setUp() {
        logger = (Logger) LoggerFactory.getLogger(BellaApiResponseAdvice.class);
        oldLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
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
    public void exceptionHandler_bizParamCheckException_doesNotLog() {
        BellaResponse<?> response = advice.exceptionHandler(new BizParamCheckException("用户的Apikey不存在"));

        assertEquals(Integer.valueOf(400), Integer.valueOf(response.getCode()));
        assertEquals("用户的Apikey不存在", response.getMessage());
        assertNull(response.getStacktrace());

        List<ILoggingEvent> events = appender.list;
        assertTrue(events.isEmpty());
    }

    @Test
    public void exceptionHandler_serverError_keepsThrowableInLogAndResponse() {
        BellaResponse<?> response = advice.exceptionHandler(new RuntimeException("server failed"));

        assertEquals(Integer.valueOf(500), Integer.valueOf(response.getCode()));
        assertEquals("server failed", response.getMessage());
        assertTrue(response.getStacktrace().contains("server failed"));

        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size());
        assertEquals(Level.WARN, events.get(0).getLevel());
        assertEquals("server failed", events.get(0).getFormattedMessage());
        assertTrue(events.get(0).getThrowableProxy() != null);
    }
}
