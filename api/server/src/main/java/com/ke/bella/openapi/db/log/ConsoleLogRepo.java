package com.ke.bella.openapi.db.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.CostLogHandler;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ConsoleLogRepo implements LogRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(CostLogHandler.class);

    @Override
    public void record(EndpointProcessData log) {
        LOGGER.info(JacksonUtils.serialize(log));
    }
}
