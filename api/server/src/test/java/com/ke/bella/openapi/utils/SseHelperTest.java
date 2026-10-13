package com.ke.bella.openapi.utils;

import org.junit.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SseHelperTest {

    @Test
    public void completedEmitterRejectsWritesFromLateCallbacks() {
        SseEmitter emitter = SseHelper.createSse(30_000L, "request-id");

        emitter.complete();

        assertFalse(SseHelper.trySendEvent(emitter, "trace-id", "ak-code", "external-id", null, "late-data"));
    }

    @Test
    public void openEmitterAcceptsWrites() {
        SseEmitter emitter = SseHelper.createSse(30_000L, "request-id");

        assertTrue(SseHelper.trySendEvent(emitter, "trace-id", "ak-code", "external-id", null, "data"));
    }
}
