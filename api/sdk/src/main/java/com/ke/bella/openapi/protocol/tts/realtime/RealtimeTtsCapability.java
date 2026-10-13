package com.ke.bella.openapi.protocol.tts.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RealtimeTtsCapability {
    @JsonProperty("clear_text_buffer")
    private String clearTextBuffer;
    private String cancel;
    private Boolean timestamps;
    @JsonProperty("connection_reuse")
    private Boolean connectionReuse;
    @JsonProperty("audio_transport")
    private String audioTransport;

    public static RealtimeTtsCapability huoshan(boolean timestamps) {
        return RealtimeTtsCapability.builder()
                .clearTextBuffer("unsupported")
                .cancel("supported")
                .timestamps(timestamps)
                .connectionReuse(true)
                .audioTransport("binary")
                .build();
    }
}
