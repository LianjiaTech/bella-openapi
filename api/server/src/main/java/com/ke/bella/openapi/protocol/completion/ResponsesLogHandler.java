package com.ke.bella.openapi.protocol.completion;

import com.google.common.collect.ImmutableMap;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.DateTimeUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class ResponsesLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData processData) {
        long startTime = processData.getRequestTime();
        ResponsesApiResponse response = null;

        if(processData.getResponse() instanceof ResponsesApiResponse) {
            response = (ResponsesApiResponse) processData.getResponse();
        }

        if(StringUtils.isNotBlank(processData.getResponseRaw()) && processData.getResponse() == null) {
            response = JacksonUtils.deserialize(processData.getResponseRaw(), ResponsesApiResponse.class);
        }

        if(StringUtils.isNotBlank(processData.getRequestRaw())) {
            ResponsesApiRequest request = JacksonUtils.deserialize(processData.getRequestRaw(), ResponsesApiRequest.class);
            processData.setRequest(request);
        }

        long created = response == null || response.getCreated() <= 0
                ? DateTimeUtils.getCurrentSeconds()
                : response.getCreated();
        long firstPackageTime = processData.getFirstPackageTime();

        ResponsesApiResponse.Usage usage;
        if(response == null || response.getUsage() == null) {
            log.warn("No response usage for requestId: {}, endpoint: {}",
                    processData.getRequestId(), processData.getEndpoint());
            usage = new ResponsesApiResponse.Usage();
            usage.setInput_tokens(0);
            usage.setOutput_tokens(0);
            usage.setTotal_tokens(0);
        } else {
            usage = response.getUsage();
        }

        processData.setUsage(usage);
        processData.setMetrics(countMetrics(startTime, processData.getRequestMillis(), created, firstPackageTime, usage));
    }

    @Override
    public String endpoint() {
        return "/v1/responses";
    }


    private Map<String, Object> countMetrics(long startTime, long startMills, long endTime, long firstPackageTime,
            ResponsesApiResponse.Usage usage) {
        int inputToken = usage.getInput_tokens() != null ? usage.getInput_tokens() : 0;
        int outputToken = usage.getOutput_tokens() != null ? usage.getOutput_tokens() : 0;
        int ttft = 0;
        if(firstPackageTime != 0) {
            ttft = (int) (firstPackageTime - startMills);
        }
        int ttlt = (int) (endTime - startTime);
        return ImmutableMap.of("ttft", ttft, "ttlt", ttlt, "input_token", inputToken, "output_token", outputToken);
    }
}
