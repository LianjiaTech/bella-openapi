package com.ke.bella.openapi.protocol.live.session;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.limiter.ConcurrentPermitLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LiveSessionAdmissionService {
    private static final String KEY_PREFIX = "bella-openapi-live-concurrent:";

    @Autowired
    private ConcurrentPermitLimiter permitLimiter;

    @Value("${bella.openapi.live.max-concurrent-per-ak:5}")
    private int maxConcurrentPerAk;
    @Value("${bella.openapi.live.concurrent-permit-ttl-seconds:7200}")
    private int permitTtlSeconds;

    public Lease acquire(String akCode) {
        String key = KEY_PREFIX + akCode;
        String permitId = permitLimiter.tryAcquire(key, maxConcurrentPerAk, permitTtlSeconds);
        if(permitId == null) {
            throw new BellaException.RateLimitException("Live Session 并发数已达上限");
        }
        return new Lease(permitLimiter, key, permitId);
    }

    public static final class Lease implements AutoCloseable {
        private final ConcurrentPermitLimiter permitLimiter;
        private final String key;
        private final String permitId;
        private boolean closed;

        private Lease(ConcurrentPermitLimiter permitLimiter, String key, String permitId) {
            this.permitLimiter = permitLimiter;
            this.key = key;
            this.permitId = permitId;
        }

        @Override
        public synchronized void close() {
            if(!closed) {
                closed = true;
                permitLimiter.tryRelease(key, permitId);
            }
        }
    }
}
