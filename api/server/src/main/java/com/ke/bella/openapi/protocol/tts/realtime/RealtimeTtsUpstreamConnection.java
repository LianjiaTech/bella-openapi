package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.protocol.Callbacks;
import lombok.Getter;
import lombok.Setter;
import okhttp3.WebSocket;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RealtimeTtsUpstreamConnection {
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 60_000L;

    @Getter
    private final RealtimeTtsAdaptor adaptor;
    @Getter
    private final Callbacks.WebSocketCallback callback;
    @Getter
    private final RealtimeTtsUpstreamKey key;
    @Getter
    private final long openedAtMillis;
    @Getter
    private volatile long lastUsedAtMillis;
    @Getter
    @Setter
    private volatile WebSocket webSocket;
    @Getter
    @Setter
    private volatile boolean healthy = true;
    @Getter
    @Setter
    private volatile boolean connectionStarted;
    @Getter
    @Setter
    private volatile long connectionStartedAtMillis;
    @Getter
    @Setter
    private volatile String activeProviderSessionId;
    @Getter
    @Setter
    private volatile String closeReason;
    private final Map<String, RealtimeTtsTaskContext> sessions = new ConcurrentHashMap<>();

    public RealtimeTtsUpstreamConnection(RealtimeTtsAdaptor adaptor, Callbacks.WebSocketCallback callback,
            RealtimeTtsUpstreamKey key, long openedAtMillis) {
        this.adaptor = adaptor;
        this.callback = callback;
        this.key = key;
        this.openedAtMillis = openedAtMillis;
        this.lastUsedAtMillis = openedAtMillis;
    }

    public void register(RealtimeTtsTaskContext taskContext) {
        sessions.put(taskContext.getProviderSessionId(), taskContext);
    }

    public void unregister(RealtimeTtsTaskContext taskContext) {
        if(taskContext != null) {
            sessions.remove(taskContext.getProviderSessionId(), taskContext);
        }
    }

    public RealtimeTtsTaskContext getSession(String providerSessionId) {
        return sessions.get(providerSessionId);
    }

    public RealtimeTtsTaskContext activeTask() {
        String sessionId = activeProviderSessionId;
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public boolean hasActiveSession() {
        return activeProviderSessionId != null;
    }

    public boolean clearActiveSession(RealtimeTtsTaskContext taskContext) {
        return taskContext != null && Objects.equals(activeProviderSessionId, taskContext.getProviderSessionId())
                && compareAndClearActive(taskContext.getProviderSessionId());
    }

    private synchronized boolean compareAndClearActive(String providerSessionId) {
        if(!Objects.equals(activeProviderSessionId, providerSessionId)) {
            return false;
        }
        activeProviderSessionId = null;
        return true;
    }

    public void markUsed(long now) {
        lastUsedAtMillis = now;
    }

    public boolean canReuseFor(RealtimeTtsUpstreamKey expectedKey, RealtimeTtsProperty property, RealtimeTtsCapability capability, long now) {
        return Boolean.TRUE.equals(property == null ? Boolean.TRUE : property.getConnectionReuse())
                && capability != null && Boolean.TRUE.equals(capability.getConnectionReuse())
                && healthy
                && connectionStarted
                && webSocket != null
                && !hasActiveSession()
                && Objects.equals(key, expectedKey)
                && now - lastUsedAtMillis <= DEFAULT_IDLE_TIMEOUT_MS;
    }

    public String rejectionReason(RealtimeTtsUpstreamKey expectedKey, RealtimeTtsProperty property, RealtimeTtsCapability capability, long now) {
        if(!Boolean.TRUE.equals(property == null ? Boolean.TRUE : property.getConnectionReuse())
                || capability == null || !Boolean.TRUE.equals(capability.getConnectionReuse())) {
            return "reuse_disabled";
        }
        if(!Objects.equals(key, expectedKey)) {
            return "connection_key_changed";
        }
        if(!healthy || !connectionStarted || webSocket == null) {
            return "unhealthy";
        }
        if(hasActiveSession()) {
            return "active_session_exists";
        }
        if(now - lastUsedAtMillis > DEFAULT_IDLE_TIMEOUT_MS) {
            return "idle_timeout";
        }
        return "reuse_disabled";
    }
}
