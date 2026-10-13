package com.ke.bella.openapi.protocol.speaker;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

/**
 * Property class for speaker embedding providers
 */
@Data
public class SpeakerEmbeddingProperty {
    private String encodingType = StringUtils.EMPTY;
}
