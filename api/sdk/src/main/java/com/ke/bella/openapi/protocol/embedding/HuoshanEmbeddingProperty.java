package com.ke.bella.openapi.protocol.embedding;

import com.ke.bella.openapi.protocol.AuthorizationProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class HuoshanEmbeddingProperty extends EmbeddingProperty {
    AuthorizationProperty auth;
    String deployName;
    Integer batchSize;
}
