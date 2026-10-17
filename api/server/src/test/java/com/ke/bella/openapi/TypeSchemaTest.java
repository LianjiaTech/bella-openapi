package com.ke.bella.openapi;

import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.systemone.SystemOneProperty;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TypeSchemaTest {

    @Test
    public void safetyCheckModeIsEnum() {
        TypeSchema safetyMode = JsonSchema.toSchema(CompletionProperty.class).getParams().stream()
                .filter(param -> param.getCode().equals("safetyCheckMode"))
                .findFirst()
                .orElseThrow(AssertionError::new);

        assertEquals("安全检测模式", safetyMode.getName());
        assertEquals("enum", safetyMode.getValueType());
        assertEquals(Arrays.asList("skip", "async", "sync"), safetyMode.getSelections());
    }

    @Test
    public void sessionIdIsOptionalStringHeaderConfiguration() {
        TypeSchema sessionId = JsonSchema.toSchema(CompletionProperty.class).getParams().stream()
                .filter(param -> param.getCode().equals("sessionId"))
                .findFirst()
                .orElseThrow(AssertionError::new);

        assertEquals("SessionId Header", sessionId.getName());
        assertEquals("string", sessionId.getValueType());
    }

    @Test
    public void systemOneHeadersAreMapConfiguration() {
        TypeSchema headers = JsonSchema.toSchema(SystemOneProperty.class).getParams().stream()
                .filter(param -> param.getCode().equals("headers"))
                .findFirst()
                .orElseThrow(AssertionError::new);

        assertEquals("额外请求头", headers.getName());
        assertEquals("map", headers.getValueType());
    }
}
