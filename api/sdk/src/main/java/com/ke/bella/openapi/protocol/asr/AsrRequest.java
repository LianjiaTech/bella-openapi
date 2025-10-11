package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ke.bella.openapi.ISummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AsrRequest implements ISummary {
    @JsonIgnore
    byte[] content;
    String model;
    String format;
    Integer maxSentenceSilence;
    Integer sampleRate;
    String hotWords;
    String hotWordsTableId;

    @Override
    public String[] ignoreFields() {
        return new String[]{ "content" };
    }
}
