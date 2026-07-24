package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.ResponsesApiRequest;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class RouteAffinityKeyGeneratorTest {
    private static final String ENDPOINT = "/v1/chat/completions";
    private static final String MODEL = "gpt-test";

    @Test
    public void forChat_shortStablePrefix_returnsNull() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        CompletionRequest request = CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder().role("user").content("hello").build()))
                .build();

        assertThat(generator.forChat(ENDPOINT, MODEL, request)).isNull();
    }

    @Test
    public void forChat_ignoresLastUserAndTools() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        CompletionRequest first = CompletionRequest.builder()
                .messages(Arrays.asList(
                        Message.builder().role("system").content("stable-prefix-abcdefghijklmnop").build(),
                        Message.builder().role("user").content("question one").build()))
                .tools(Collections.singletonList(Message.Tool.builder()
                        .type("function")
                        .function(Message.Function.builder().name("tool_a").build())
                        .build()))
                .build();
        CompletionRequest second = CompletionRequest.builder()
                .messages(Arrays.asList(
                        Message.builder().role("system").content("stable-prefix-abcdefghijklmnop").build(),
                        Message.builder().role("user").content("question two").build()))
                .tools(Collections.singletonList(Message.Tool.builder()
                        .type("function")
                        .function(Message.Function.builder().name("tool_b").build())
                        .build()))
                .build();

        assertThat(generator.forChat(ENDPOINT, MODEL, first))
                .isEqualTo(generator.forChat(ENDPOINT, MODEL, second));
    }

    @Test
    public void forChat_usesSeparateHashPrefixAndMinPrefixBytes() {
        RouteAffinityKeyGenerator generator = generator(true, 16, 8);
        CompletionRequest first = CompletionRequest.builder()
                .messages(Arrays.asList(
                        Message.builder().role("system").content("same-prefix-1234567890-tail-one").build(),
                        Message.builder().role("user").content("question one").build()))
                .build();
        CompletionRequest second = CompletionRequest.builder()
                .messages(Arrays.asList(
                        Message.builder().role("system").content("same-prefix-1234567890-tail-two").build(),
                        Message.builder().role("user").content("question two").build()))
                .build();

        assertThat(generator.forChat(ENDPOINT, MODEL, first))
                .isNotNull()
                .isEqualTo(generator.forChat(ENDPOINT, MODEL, second));
    }

    @Test
    public void forChat_countsUtf8BytesForMinimumPrefix() {
        RouteAffinityKeyGenerator generator = generator(true, 16, 9);
        CompletionRequest request = CompletionRequest.builder()
                .messages(Arrays.asList(
                        Message.builder().role("system").content("中文中").build(),
                        Message.builder().role("user").content("question").build()))
                .build();

        assertThat(generator.forChat(ENDPOINT, MODEL, request)).isNotNull();
    }

    @Test
    public void forMessages_ignoresLastUserAndTools() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        MessageRequest first = MessageRequest.builder()
                .system("stable-prefix-abcdefghijklmnop")
                .messages(Collections.singletonList(MessageRequest.InputMessage.builder()
                        .role("user")
                        .content("question one")
                        .build()))
                .tools(Collections.singletonList(MessageRequest.Tool.builder().name("tool_a").build()))
                .build();
        MessageRequest second = MessageRequest.builder()
                .system("stable-prefix-abcdefghijklmnop")
                .messages(Collections.singletonList(MessageRequest.InputMessage.builder()
                        .role("user")
                        .content("question two")
                        .build()))
                .tools(Collections.singletonList(MessageRequest.Tool.builder().name("tool_b").build()))
                .build();

        assertThat(generator.forMessages("/v1/messages", MODEL, first))
                .isEqualTo(generator.forMessages("/v1/messages", MODEL, second));
    }

    @Test
    public void forResponses_stringInputUsesEarlyAnchorOnly() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        ResponsesApiRequest first = ResponsesApiRequest.builder()
                .input("stable-prefix-abcdefghijklmnop tail one")
                .build();
        ResponsesApiRequest second = ResponsesApiRequest.builder()
                .input("stable-prefix-abcdefghijklmnop tail two")
                .build();

        assertThat(generator.forResponses("/v1/responses", MODEL, first))
                .isEqualTo(generator.forResponses("/v1/responses", MODEL, second));
    }

    @Test
    public void forResponses_listInputExcludesLastUserItem() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        ResponsesApiRequest first = ResponsesApiRequest.builder()
                .input(Arrays.asList(inputItem("user", "stable-prefix-abcdefghijklmnop"),
                        inputItem("user", "question one")))
                .build();
        ResponsesApiRequest second = ResponsesApiRequest.builder()
                .input(Arrays.asList(inputItem("user", "stable-prefix-abcdefghijklmnop"),
                        inputItem("user", "question two")))
                .build();

        assertThat(generator.forResponses("/v1/responses", MODEL, first))
                .isEqualTo(generator.forResponses("/v1/responses", MODEL, second));
    }

    @Test
    public void disabled_returnsNull() {
        RouteAffinityKeyGenerator generator = generator(false, 16);
        CompletionRequest request = CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder()
                        .role("system")
                        .content("stable-prefix-abcdefghijklmnop")
                        .build()))
                .build();

        assertThat(generator.forChat(ENDPOINT, MODEL, request)).isNull();
    }

    private Map<String, Object> inputItem(String role, String text) {
        Map<String, Object> item = new HashMap<>();
        item.put("type", "message");
        item.put("role", role);
        item.put("content", text);
        return item;
    }

    private RouteAffinityKeyGenerator generator(boolean enabled, int hashPrefixBytes) {
        return generator(enabled, hashPrefixBytes, hashPrefixBytes);
    }

    private RouteAffinityKeyGenerator generator(boolean enabled, int hashPrefixBytes, int minPrefixBytes) {
        RouteAffinityKeyGenerator generator = new RouteAffinityKeyGenerator();
        ReflectionTestUtils.setField(generator, "enabled", enabled);
        ReflectionTestUtils.setField(generator, "hashPrefixBytes", hashPrefixBytes);
        ReflectionTestUtils.setField(generator, "minPrefixBytes", minPrefixBytes);
        return generator;
    }
}
