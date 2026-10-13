package com.ke.bella.openapi.protocol.video;

import org.springframework.stereotype.Component;
import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class VideoLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData processData) {
        if(processData.getRequest() == null && StringUtils.isNotBlank(processData.getRequestRaw())) {
            VideoCreateRequest request = JacksonUtils.deserialize(processData.getRequestRaw(), VideoCreateRequest.class);
            if(request != null) {
                processData.setRequest(request);
            }
        }

        if(processData.getResponse() == null && StringUtils.isNotBlank(processData.getResponseRaw())) {
            VideoJob response = JacksonUtils.deserialize(processData.getResponseRaw(), VideoJob.class);
            if(response != null) {
                processData.setResponse(response);
                if(processData.getUsage() == null && response.getUsage() != null) {
                    processData.setUsage(response.getUsage());
                }
            }
        }

        log.debug("Video log handler: requestId={}", processData.getRequestId());
    }

    @Override
    public String endpoint() {
        return "/v1/videos";
    }
}
