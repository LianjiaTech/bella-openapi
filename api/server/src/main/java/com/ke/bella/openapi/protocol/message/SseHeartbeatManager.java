package com.ke.bella.openapi.protocol.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class SseHeartbeatManager {
    private static final long SCAN_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(1);
    private static final SseHeartbeatManager INSTANCE = new SseHeartbeatManager(
            createScheduler(), System::currentTimeMillis, SCAN_INTERVAL_MILLIS);

    private final Map<SseEmitter, Connection> connections = new ConcurrentHashMap<>();
    private final LongSupplier currentTimeMillis;

    static SseHeartbeatManager getInstance() {
        return INSTANCE;
    }

    private static ScheduledExecutorService createScheduler() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "messages-sse-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    SseHeartbeatManager(ScheduledExecutorService scheduler, LongSupplier currentTimeMillis,
            long scanIntervalMillis) {
        this.currentTimeMillis = currentTimeMillis;
        scheduler.scheduleAtFixedRate(this::checkHeartbeats,
                scanIntervalMillis, scanIntervalMillis, TimeUnit.MILLISECONDS);
    }

    Registration register(SseEmitter sse, long heartbeatIntervalMillis, Runnable heartbeatSender) {
        Connection connection = new Connection(sse, heartbeatIntervalMillis, heartbeatSender,
                currentTimeMillis.getAsLong());
        connections.put(sse, connection);
        return connection;
    }

    int connectionCount() {
        return connections.size();
    }

    private void checkHeartbeats() {
        long currentTime = currentTimeMillis.getAsLong();
        connections.values().forEach(connection -> connection.sendHeartbeatIfIdle(currentTime));
    }

    interface Registration {
        void recordActivity();

        void unregister();
    }

    private class Connection implements Registration {
        private final SseEmitter sse;
        private final long heartbeatIntervalMillis;
        private final Runnable heartbeatSender;

        private long lastActivityTime;
        private boolean active = true;

        private Connection(SseEmitter sse, long heartbeatIntervalMillis,
                Runnable heartbeatSender, long lastActivityTime) {
            this.sse = sse;
            this.heartbeatIntervalMillis = heartbeatIntervalMillis;
            this.heartbeatSender = heartbeatSender;
            this.lastActivityTime = lastActivityTime;
        }

        @Override
        public synchronized void recordActivity() {
            if(active) {
                lastActivityTime = currentTimeMillis.getAsLong();
            }
        }

        @Override
        public synchronized void unregister() {
            if(!active) {
                return;
            }
            active = false;
            connections.remove(sse, this);
        }

        private synchronized void sendHeartbeatIfIdle(long currentTime) {
            if(!active || currentTime - lastActivityTime < heartbeatIntervalMillis) {
                return;
            }
            try {
                heartbeatSender.run();
                lastActivityTime = currentTimeMillis.getAsLong();
            } catch (RuntimeException e) {
                log.warn("Failed to send SSE heartbeat", e);
                unregister();
            }
        }
    }
}
