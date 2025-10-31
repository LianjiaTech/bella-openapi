package com.ke.bella.openapi.protocol.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.metrics.MetricsManager;
import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetricsLogHandler implements EventHandler<LogEvent> {

    private final MetricsManager metricsManager;

    public MetricsLogHandler(MetricsManager metricsManager) {
        this.metricsManager = metricsManager;
    }

    @Override
    public void onEvent(LogEvent event, long sequence, boolean endOfBatch) throws Exception {
        EndpointProcessData log = event.getData();
        if(!log.isInnerLog() || event.isCostOnly()) {
            return;
        }
        metricsManager.record(log);
    }

}
