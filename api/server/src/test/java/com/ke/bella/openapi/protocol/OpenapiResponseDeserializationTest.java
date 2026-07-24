package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class OpenapiResponseDeserializationTest {

    @Test
    public void deserializeCompletionResponseWithStringError() {
        String json = "{\"error\":\"Too many requests. Please try again later.\"}";

        CompletionResponse response = JacksonUtils.deserialize(json, CompletionResponse.class);

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals("Too many requests. Please try again later.", response.getError().getMessage());
        assertEquals(Integer.valueOf(400), response.getError().getHttpCode());
        assertNull(response.getError().getType());
        assertNull(response.getError().getCode());
    }

    @Test
    public void deserializeStreamCompletionResponseWithStringError() {
        String json = "{\"error\":\"Too many requests. Please try again later.\"}";

        StreamCompletionResponse response = JacksonUtils.deserialize(json, StreamCompletionResponse.class);

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals("Too many requests. Please try again later.", response.getError().getMessage());
        assertEquals(Integer.valueOf(400), response.getError().getHttpCode());
        assertNull(response.getError().getType());
        assertNull(response.getError().getCode());
    }

    @Test
    public void deserializeCompletionResponseWithObjectError() {
        String json = "{\"error\":{\"type\":\"rate_limit_error\",\"message\":\"Too many requests.\",\"code\":\"rate_limit\"}}";

        CompletionResponse response = JacksonUtils.deserialize(json, CompletionResponse.class);

        assertNotNull(response);
        assertNotNull(response.getError());
        assertEquals("rate_limit_error", response.getError().getType());
        assertEquals("Too many requests.", response.getError().getMessage());
        assertEquals("rate_limit", response.getError().getCode());
        assertEquals(Integer.valueOf(400), response.getError().getHttpCode());
    }

    @Test
    public void deserializeCompletionResponseReasoningAliasAsReasoningContent() {
        String json = "{"
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"reasoning\":\"think\",\"content\":\"answer\"}}]"
                + "}";

        CompletionResponse response = JacksonUtils.deserialize(json, CompletionResponse.class);

        assertEquals("think", response.reasoning());
        String serialized = JacksonUtils.serialize(response);
        assertTrue(serialized.contains("\"reasoning_content\":\"think\""));
        assertFalse(serialized.contains("\"reasoning\":"));
    }

    @Test
    public void deserializeCompletionResponseReasonAliasAsReasoningContent() {
        String json = "{"
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"reason\":\"think\",\"content\":\"answer\"}}]"
                + "}";

        CompletionResponse response = JacksonUtils.deserialize(json, CompletionResponse.class);

        assertEquals("think", response.reasoning());
        String serialized = JacksonUtils.serialize(response);
        assertTrue(serialized.contains("\"reasoning_content\":\"think\""));
        assertFalse(serialized.contains("\"reason\":"));
    }

    @Test
    public void deserializeCompletionResponseReasoningContentTakesPrecedenceOverAlias() {
        String aliasFirst = "{"
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"reason\":\"alias\",\"reasoning_content\":\"canonical\"}}]"
                + "}";
        String canonicalFirst = "{"
                + "\"choices\":[{\"message\":{\"role\":\"assistant\",\"reasoning_content\":\"canonical\",\"reasoning\":\"alias\"}}]"
                + "}";

        assertEquals("canonical", JacksonUtils.deserialize(aliasFirst, CompletionResponse.class).reasoning());
        assertEquals("canonical", JacksonUtils.deserialize(canonicalFirst, CompletionResponse.class).reasoning());
    }

    @Test
    public void deserializeStreamCompletionResponseReasoningAliasAsReasoningContent() {
        String json = "{"
                + "\"choices\":[{\"delta\":{\"role\":\"assistant\",\"reasoning\":\"think-delta\"}}]"
                + "}";

        StreamCompletionResponse response = JacksonUtils.deserialize(json, StreamCompletionResponse.class);

        assertEquals("think-delta", response.reasoning());
        String serialized = JacksonUtils.serialize(response);
        assertTrue(serialized.contains("\"reasoning_content\":\"think-delta\""));
        assertFalse(serialized.contains("\"reasoning\":"));
    }
}
