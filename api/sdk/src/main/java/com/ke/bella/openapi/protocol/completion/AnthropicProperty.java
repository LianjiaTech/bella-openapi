package com.ke.bella.openapi.protocol.completion;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ke.bella.openapi.protocol.AuthorizationProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnthropicProperty extends CompletionProperty {

    @Override
    public String getAnthropicVersion() {
        if(anthropicVersion == null) {
            return "2023-06-01";
        }
        return anthropicVersion;
    }
}
