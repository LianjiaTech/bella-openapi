package com.ke.bella.openapi.utils;

import java.io.IOException;

import com.ke.bella.openapi.BellaContext;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SseHelper {
    public static SseEmitter createSse(long timeout, String reqId) {
        final String traceId = BellaContext.getTraceId();
        SseEmitter sse = new SseEmitter(timeout);

        sse.onCompletion(() -> log.info("[traceId={}][{}] 结束连接...................", traceId, reqId));
        sse.onTimeout(() -> log.info("[traceId={}][{}] 连接超时...................", traceId, reqId));
        sse.onError(e -> log.info("[traceId={}][{}] 连接异常,{}", traceId, reqId, e.toString()));
        log.info("[traceId={}][{}] 创建sse连接成功！", traceId, reqId);

        return sse;
    }

    public static void send(SseEmitter sse, SseEventBuilder event) {
        try {
            sse.send(event);
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    public static void sendEvent(SseEmitter sse, String event, Object data) {
        send(sse, SseEmitter.event().name(event).data(data));
    }

    public static void sendEvent(SseEmitter sse, Object data) {
        send(sse, SseEmitter.event().data(data));
    }

}
