package com.ke.bella.openapi.protocol.asr.realtime;

import com.ke.bella.openapi.protocol.realtime.RealTimeLogHandler;
import org.springframework.stereotype.Component;

@Component
public class RealTimeAsrLogHandler extends RealTimeLogHandler {
    @Override
    public String endpoint() {
        return "/v1/audio/asr/stream";
    }
}
