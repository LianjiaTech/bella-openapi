package com.ke.bella.openapi.protocol.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.lmax.disruptor.RingBuffer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EndpointLogger {
    @Autowired
    private RingBuffer<LogEvent> ringBuffer;
    @Autowired
    private List<EndpointLogHandler> logHandlers;
    private Map<String, EndpointLogHandler> handlerMap;
    @Value("${openapi.log.repo:consoleLogRepo}")
    private String logRepo;
    @PostConstruct
    public void init() {
        handlerMap = new HashMap<>();
        logHandlers.forEach(handler -> handlerMap.put(handler.endpoint(), handler));
    }
    public void log(EndpointProcessData log) {
        log.setApikey(null);
        if(log.isMock()) {
            return;
        }
        EndpointLogHandler handler = handlerMap.get(log.getEndpoint());
        if(handler != null) {
            handler.process(log);
        }
        long sequence = ringBuffer.next();
        LogEvent event = ringBuffer.get(sequence);
        event.setData(log);
        event.setRepositoryCode(logRepo);
        if(log.isBatch()) {
            log.setInnerLog(true);
            event.setCostOnly(true);
        }
        ringBuffer.publish(sequence);
    }
}
