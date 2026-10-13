package com.ke.bella.openapi.protocol.asr.realtime;

import com.ke.bella.openapi.protocol.realtime.RealtimeProperty;
import org.springframework.stereotype.Component;

@Component("KeRealtimeAsr")
public class KeAdaptor extends com.ke.bella.openapi.protocol.realtime.KeAdaptor implements RealTimeAsrAdaptor<RealtimeProperty> {
}
