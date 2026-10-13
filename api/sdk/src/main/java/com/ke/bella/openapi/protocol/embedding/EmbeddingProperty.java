package com.ke.bella.openapi.protocol.embedding;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class EmbeddingProperty {
    String encodingType = StringUtils.EMPTY;
}
