package com.ke.bella.openapi.protocol.tts.realtime;

import com.ke.bella.openapi.protocol.tts.TtsProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RealtimeTtsProperty extends TtsProperty {
    private Boolean connectionReuse = true;
}
