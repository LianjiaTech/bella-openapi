package com.ke.bella.openapi.protocol.video;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.log.EndpointLogHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class VideoLogHandler implements EndpointLogHandler {

    @Override
    public void process(EndpointProcessData processData) {
        // Video的usage在callback时才能获取，这里不做处理
        // 实际的usage会在VideoCallbackService中通过processData.setUsage()设置
        log.debug("Video log handler: requestId={}", processData.getRequestId());
    }

    @Override
    public String endpoint() {
        return "/v1/videos";
    }
}
