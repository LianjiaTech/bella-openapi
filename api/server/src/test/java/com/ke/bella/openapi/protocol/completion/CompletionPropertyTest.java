package com.ke.bella.openapi.protocol.completion;

import com.ke.bella.openapi.safety.SafetyCheckMode;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompletionPropertyTest {

    @Test
    void defaultsToSyncAcrossLlmProperties() {
        assertEquals(SafetyCheckMode.sync, deserialize("{}", OpenAIProperty.class).getSafetyCheckMode());
        assertEquals(SafetyCheckMode.sync, deserialize("{}", AnthropicProperty.class).getSafetyCheckMode());
        assertEquals(SafetyCheckMode.sync, deserialize("{}", ResponsesApiProperty.class).getSafetyCheckMode());
    }

    @Test
    void preservesExplicitSafetyCheckModes() {
        assertEquals(SafetyCheckMode.async, deserialize("{\"safetyCheckMode\":\"async\"}", CompletionProperty.class).getSafetyCheckMode());
        assertEquals(SafetyCheckMode.sync, deserialize("{\"safetyCheckMode\":\"sync\"}", CompletionProperty.class).getSafetyCheckMode());
        assertEquals(SafetyCheckMode.skip, deserialize("{\"safetyCheckMode\":\"skip\"}", CompletionProperty.class).getSafetyCheckMode());
    }

    private <T extends CompletionProperty> T deserialize(String json, Class<T> type) {
        return JacksonUtils.deserialize(json, type);
    }
}
