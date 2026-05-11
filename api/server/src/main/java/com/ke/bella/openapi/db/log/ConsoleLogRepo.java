package com.ke.bella.openapi.db.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.utils.CompressUtils;
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

    @Value("${bella.log.compress-threshold-bytes:10240}")
    private int compressThresholdBytes;

    @Override
    public void record(EndpointProcessData log) {
        String serialized = JacksonUtils.serialize(log);

        FULL_LOGGER.info(serialized);

        // Compress large fields instead of removing them
        EndpointProcessData outputLog = log;
        boolean needsCompression = false;

        String requestStr = JacksonUtils.serialize(log.getRequestForLogging());
        String responseStr = JacksonUtils.serialize(log.getResponse());

        if (requestStr.getBytes().length > compressThresholdBytes) {
            needsCompression = true;
        }
        if (responseStr.getBytes().length > compressThresholdBytes) {
            needsCompression = true;
        }

        if (needsCompression) {
            outputLog = new EndpointProcessData();
            BeanUtils.copyProperties(log, outputLog);

            if (requestStr.getBytes().length > compressThresholdBytes) {
                outputLog.setRequest(CompressUtils.compress(requestStr));
                outputLog.setRequestCompressed(true);
            }
            if (responseStr.getBytes().length > compressThresholdBytes) {
                outputLog.setResponse(null);
                outputLog.setResponseCompressed(true);
                outputLog.setResponseRaw(CompressUtils.compress(responseStr));
            }

            serialized = JacksonUtils.serialize(outputLog);
        }

        // Fallback: if compressed log still exceeds max-size-bytes, truncate
        if (maxLogSizeBytes != null && serialized.getBytes().length > maxLogSizeBytes) {
            EndpointProcessData reducedLog = new EndpointProcessData();
            BeanUtils.copyProperties(outputLog, reducedLog);
            reducedLog.setRequest("[REMOVED: Log size exceeded " + maxLogSizeBytes + " bytes]");
            reducedLog.setResponse(null);
            reducedLog.setResponseRaw(null);
            reducedLog.setRequestCompressed(false);
            reducedLog.setResponseCompressed(false);

            serialized = JacksonUtils.serialize(reducedLog);
        }

        LOGGER.info(serialized);
    }
}
