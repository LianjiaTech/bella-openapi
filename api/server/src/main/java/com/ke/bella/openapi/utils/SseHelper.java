package com.ke.bella.openapi.utils;

import java.io.IOException;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SseHelper {
    public static SseEmitter createSse(long timeout, String reqId) {
        SseEmitter sse = new SseEmitter(timeout);

        sse.onCompletion(() -> LOGGER.info("[{}] 结束连接...................", reqId));
        sse.onTimeout(() -> LOGGER.info("[{}]连接超时...................", reqId));
        sse.onError(e -> LOGGER.info("[{}]连接异常,{}", reqId, e.toString()));
        LOGGER.info("[{}]创建sse连接成功！", reqId);

        return sse;
    }

    public static void send(SseEmitter sse, SseEventBuilder event) {
        try {
            sse.send(event);
        } catch (IOException e) {
            LOGGER.warn(e.getMessage(), e);
        }
    }

    public static void sendEvent(SseEmitter sse, String event, Object data) {
        send(sse, SseEmitter.event().name(event).data(data));
    }

    public static void sendEvent(SseEmitter sse, Object data) {
        send(sse, SseEmitter.event().data(data));
    }

}
