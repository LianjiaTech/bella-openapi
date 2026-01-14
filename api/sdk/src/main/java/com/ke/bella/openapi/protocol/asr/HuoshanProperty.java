package com.ke.bella.openapi.protocol.asr;

import lombok.Data;

@Data
public class HuoshanProperty extends AsrProperty {
    String resultType; // full single
    String appid;
    int chunkSize = 3200;
    int intervalMs = 100;
}
