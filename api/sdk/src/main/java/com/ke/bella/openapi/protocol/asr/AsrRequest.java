package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AsrRequest {
    @JsonIgnore
    byte[] content;
    String model;
    String format;
    Integer maxSentenceSilence;
    Integer sampleRate;
    String hotWords;
    String hotWordsTableId;
}
