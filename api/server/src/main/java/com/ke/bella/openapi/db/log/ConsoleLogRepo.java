package com.ke.bella.openapi.db.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.images.ImagesLogSanitizer;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

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

        serialized = reduceForLogging(log, serialized);

        LOGGER.info(serialized);
    }

    String reduceForLogging(EndpointProcessData log, String serialized) {
        // 短期先优先保留 response，便于排查模型输出；中长期可将 payload 裁剪逻辑抽成结构化的 LogReducer。
        if(maxLogSizeBytes != null && exceedsMaxLogSize(serialized)) {
            EndpointProcessData reducedLog = new EndpointProcessData();
            EndpointProcessData imageReducedLog = ImagesLogSanitizer.sanitizedCopy(log);
            if(imageReducedLog != null) {
                serialized = JacksonUtils.serialize(imageReducedLog);
                if(!exceedsMaxLogSize(serialized)) {
                    return serialized;
                }
                reducedLog = imageReducedLog;
            } else {
                BeanUtils.copyProperties(log, reducedLog);
            }

            reducedLog.setRequest("[REMOVED: Log size exceeded " + maxLogSizeBytes + " bytes]");

            serialized = JacksonUtils.serialize(reducedLog);
            if(exceedsMaxLogSize(serialized)) {
                reducedLog.setResponse(null);
                serialized = JacksonUtils.serialize(reducedLog);
            }
        }
        return serialized;
    }

    private boolean exceedsMaxLogSize(String serialized) {
        return serialized.getBytes(StandardCharsets.UTF_8).length > maxLogSizeBytes;
    }
}
