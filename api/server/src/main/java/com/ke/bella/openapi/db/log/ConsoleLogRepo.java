package com.ke.bella.openapi.db.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.CostLogHandler;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ConsoleLogRepo implements LogRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleLogRepo.class);
    
    @Value("${bella.log.max-size-bytes:#{null}}")
    private Integer maxLogSizeBytes;

    @Override
    public void record(EndpointProcessData log) {
        String serialized = JacksonUtils.serialize(log);
        
        // Check if log size limit is configured and serialized size exceeds it
        if (maxLogSizeBytes != null && serialized.getBytes().length > maxLogSizeBytes) {
            // Create a copy with request and response removed to reduce size
            EndpointProcessData reducedLog = new EndpointProcessData();
            BeanUtils.copyProperties(log, reducedLog);
            reducedLog.setRequest("[REMOVED: Log size exceeded " + maxLogSizeBytes + " bytes]");
            reducedLog.setResponse(null);
            
            serialized = JacksonUtils.serialize(reducedLog);
        }
        
        LOGGER.info(serialized);
    }
}
