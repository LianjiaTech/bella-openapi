package com.ke.bella.openapi.protocol.asr;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QwenProperty extends AsrProperty {
    // Inherits deployName and auth from AsrProperty
}