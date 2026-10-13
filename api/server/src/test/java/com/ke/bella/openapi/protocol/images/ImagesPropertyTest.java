package com.ke.bella.openapi.protocol.images;

import com.ke.bella.openapi.safety.SafetyCheckMode;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImagesPropertyTest {

    @Test
    void defaultsToAsyncAndEditorInheritsConfiguredMode() {
        ImagesProperty defaultProperty = JacksonUtils.deserialize("{}", ImagesProperty.class);
        ImagesEditorProperty editorProperty = JacksonUtils.deserialize("{\"safetyCheckMode\":\"sync\"}", ImagesEditorProperty.class);
        ImagesProperty skippedProperty = JacksonUtils.deserialize("{\"safetyCheckMode\":\"skip\"}", ImagesProperty.class);

        assertEquals(SafetyCheckMode.async, defaultProperty.getSafetyCheckMode());
        assertEquals(SafetyCheckMode.sync, editorProperty.getSafetyCheckMode());
        assertEquals(SafetyCheckMode.skip, skippedProperty.getSafetyCheckMode());
    }
}
