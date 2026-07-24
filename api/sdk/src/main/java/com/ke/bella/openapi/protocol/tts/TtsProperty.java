package com.ke.bella.openapi.protocol.tts;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class TtsProperty {
    String encodingType = StringUtils.EMPTY;
    String defaultContentType = "wav";
    String defaultVoice;
    Integer defaultSampleRate;
}
