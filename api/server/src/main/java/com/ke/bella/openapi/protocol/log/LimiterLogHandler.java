package com.ke.bella.openapi.protocol.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.limiter.LimiterManager;
import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LimiterLogHandler implements EventHandler<LogEvent> {

    private final LimiterManager limiterManager;

    public LimiterLogHandler(LimiterManager limiterManager) {
        this.limiterManager = limiterManager;
    }

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) throws Exception {
        EndpointProcessData log = event.getData();
        if(event.isCostOnly()) {
            return;
        }
        if(log.isInnerLog() && !log.isPrivate()) {
            limiterManager.record(log);
        }
    }
}
