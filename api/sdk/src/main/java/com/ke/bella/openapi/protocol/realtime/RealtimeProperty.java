package com.ke.bella.openapi.protocol.realtime;

import com.ke.bella.openapi.protocol.asr.AsrProperty;
import lombok.Data;

@Data
public class RealtimeProperty extends AsrProperty {
    RealTimeMessage.LlmOption llmOption;
    RealTimeMessage.TtsOption ttsOption;
}
