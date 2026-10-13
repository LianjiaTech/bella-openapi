package com.ke.bella.openapi.utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.ke.bella.openapi.BellaContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SseHelper {
    public static SseEmitter createSse(long timeout, String reqId) {
        final String traceId = BellaContext.getTraceId();
        final String akCode = BellaContext.getAkCode();
        final String xReqId = BellaContext.getExternalRequestId();
        SseEmitter sse = new ManagedSseEmitter(timeout);

        sse.onCompletion(() -> log.info("[traceId={}][reqId={}][xReqId={}][akCode={}] 结束连接...................", traceId, reqId,xReqId,akCode));
        sse.onTimeout(() -> log.info("[traceId={}][reqId={}][xReqId={}][akCode={}] 连接超时...................", traceId, reqId,xReqId,akCode));
        sse.onError(e -> log.info("[traceId={}][reqId={}][xReqId={}][akCode={}] 连接异常,{}", traceId, reqId,xReqId,akCode, StringUtils.normalizeSpace(e.getMessage())));
        log.info("[traceId={}][{}][{}] 创建sse连接成功！", traceId, reqId,akCode);

        return sse;
    }

    public static void send(SseEmitter sse, SseEventBuilder event) {
        final String traceId = BellaContext.getTraceId();
        final String akCode = BellaContext.getAkCode();
        final String xReqId = BellaContext.getExternalRequestId();
        trySend(sse, traceId, akCode, xReqId, event);
    }

    public static void sendEvent(SseEmitter sse, String event, Object data) {
        send(sse, SseEmitter.event().name(event).data(data));
    }

    public static void sendEvent(SseEmitter sse, Object data) {
        send(sse, SseEmitter.event().data(data));
    }

    /**
     * 在异步线程中发送 SSE。请求线程的 BellaContext 可能已经被清理，
     * 因此请求标识必须由调用方显式传入。
     */
    public static void sendEvent(SseEmitter sse, String traceId, String akCode,
            String xReqId, Object data) {
        sendEvent(sse, traceId, akCode, xReqId, null, data);
    }

    /**
     * 在异步线程中发送带事件名称的 SSE。请求线程的 BellaContext 可能已经被清理，
     * 因此请求标识必须由调用方显式传入。
     */
    public static void sendEvent(SseEmitter sse, String traceId, String akCode,
            String xReqId, String event, Object data) {
        trySendEvent(sse, traceId, akCode, xReqId, event, data);
    }

    public static boolean trySendEvent(SseEmitter sse, String traceId, String akCode,
            String xReqId, String event, Object data) {
        SseEventBuilder builder = SseEmitter.event().data(data);
        if(StringUtils.isNotBlank(event)) {
            builder.name(event);
        }
        return trySend(sse, traceId, akCode, xReqId, builder);
    }

    private static boolean trySend(SseEmitter sse, String traceId, String akCode,
            String xReqId, SseEventBuilder event) {
        if(sse instanceof ManagedSseEmitter && ((ManagedSseEmitter) sse).closed) {
            return false;
        }
        try {
            sse.send(event);
            return true;
        } catch (IOException e) {
            log.warn("[traceId={}][xReqId={}][{}]发送失败 [{}]", traceId, xReqId, akCode,
                    StringUtils.normalizeSpace(e.getMessage()), e);
            return false;
        } catch (IllegalStateException e) {
            log.warn("[traceId={}][xReqId={}][{}]SSE连接已结束，发送失败 [{}]", traceId, xReqId, akCode,
                    StringUtils.normalizeSpace(e.getMessage()), e);
            return false;
        }
    }

    public static void onClose(SseEmitter sse, Runnable callback) {
        if(sse instanceof ManagedSseEmitter) {
            ((ManagedSseEmitter) sse).onClose(callback);
        } else if(sse != null) {
            sse.onCompletion(callback);
            sse.onTimeout(callback);
            sse.onError(error -> callback.run());
        }
    }

    private static final class ManagedSseEmitter extends SseEmitter {
        private volatile boolean closed;
        private boolean completionHandled;
        private final List<Runnable> closeHandlers = new ArrayList<>();
        private final List<Runnable> completionHandlers = new ArrayList<>();
        private final List<Runnable> timeoutHandlers = new ArrayList<>();
        private final List<Consumer<Throwable>> errorHandlers = new ArrayList<>();

        private ManagedSseEmitter(long timeout) {
            super(timeout);
            super.onCompletion(this::handleCompletion);
            super.onTimeout(this::handleTimeout);
            super.onError(this::handleError);
        }

        @Override
        public void send(SseEventBuilder event) throws IOException {
            List<Runnable> handlers = Collections.emptyList();
            try {
                synchronized(this) {
                    if(closed) {
                        throw new IllegalStateException("SSE response is closed");
                    }
                    try {
                        super.send(event);
                    } catch (IOException | RuntimeException exception) {
                        handlers = closeLocked();
                        throw exception;
                    }
                }
            } finally {
                handlers.forEach(this::runHandler);
            }
        }

        @Override
        public void complete() {
            List<Runnable> handlers = Collections.emptyList();
            try {
                synchronized(this) {
                    if(closed) {
                        return;
                    }
                    handlers = closeLocked();
                    super.complete();
                }
            } finally {
                handlers.forEach(this::runHandler);
            }
        }

        @Override
        public void completeWithError(Throwable error) {
            List<Runnable> handlers = Collections.emptyList();
            try {
                synchronized(this) {
                    if(closed) {
                        return;
                    }
                    handlers = closeLocked();
                    super.completeWithError(error);
                }
            } finally {
                handlers.forEach(this::runHandler);
            }
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            if(!completionHandled) {
                completionHandlers.add(callback);
            }
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            if(!completionHandled) {
                timeoutHandlers.add(callback);
            }
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            if(!completionHandled) {
                errorHandlers.add(callback);
            }
        }

        private void onClose(Runnable callback) {
            synchronized(this) {
                if(!closed) {
                    closeHandlers.add(callback);
                    return;
                }
            }
            runHandler(callback);
        }

        private List<Runnable> closeLocked() {
            if(closed) {
                return Collections.emptyList();
            }
            closed = true;
            List<Runnable> handlers = new ArrayList<>(closeHandlers);
            closeHandlers.clear();
            return handlers;
        }

        private void handleCompletion() {
            List<Runnable> handlers;
            synchronized(this) {
                handlers = new ArrayList<>(closeLocked());
                handlers.addAll(completionHandlers);
                completionHandled = true;
                completionHandlers.clear();
                timeoutHandlers.clear();
                errorHandlers.clear();
            }
            handlers.forEach(this::runHandler);
        }

        private void handleTimeout() {
            List<Runnable> handlers;
            synchronized(this) {
                handlers = new ArrayList<>(closeLocked());
                handlers.addAll(timeoutHandlers);
                timeoutHandlers.clear();
                errorHandlers.clear();
            }
            handlers.forEach(this::runHandler);
        }

        private void handleError(Throwable error) {
            List<Runnable> handlers;
            synchronized(this) {
                handlers = new ArrayList<>(closeLocked());
                errorHandlers.forEach(handler -> handlers.add(() -> handler.accept(error)));
                timeoutHandlers.clear();
                errorHandlers.clear();
            }
            handlers.forEach(this::runHandler);
        }

        private void runHandler(Runnable handler) {
            try {
                handler.run();
            } catch (RuntimeException exception) {
                log.warn("SSE lifecycle callback failed", exception);
            }
        }
    }

}
