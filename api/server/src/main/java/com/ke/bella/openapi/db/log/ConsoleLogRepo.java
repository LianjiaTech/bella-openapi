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

    private static final Logger FULL_LOGGER = LoggerFactory.getLogger("ConsoleFullLogger");

    @Value("${bella.log.max-size-bytes:#{null}}")
    private Integer maxLogSizeBytes;

    @Override
    public void record(EndpointProcessData log) {
        String serialized = JacksonUtils.serialize(log);

        FULL_LOGGER.info(serialized);

        // 短期先优先保留 response，便于排查模型输出；中长期可将 payload 裁剪逻辑抽成结构化的 LogReducer。
        if(maxLogSizeBytes != null && serialized.getBytes().length > maxLogSizeBytes) {
            EndpointProcessData reducedLog = new EndpointProcessData();
            BeanUtils.copyProperties(log, reducedLog);
            reducedLog.setRequest("[REMOVED: Log size exceeded " + maxLogSizeBytes + " bytes]");

            serialized = JacksonUtils.serialize(reducedLog);
            if(serialized.getBytes().length > maxLogSizeBytes) {
                reducedLog.setResponse(null);
                serialized = JacksonUtils.serialize(reducedLog);
            }
        }

        LOGGER.info(serialized);
    }
}
