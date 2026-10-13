package com.ke.bella.openapi.protocol;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.ResponsesApiRequest;
import com.ke.bella.openapi.protocol.message.MessageRequest;
import org.junit.After;
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

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void differentSessionIdsProduceDifferentAffinityKeys() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        CompletionRequest request = CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder()
                        .role("system")
                        .content("stable content prefix abcdefghijklmnop")
                        .build()))
                .build();

        EndpointContext.getProcessData().setSessionId("session-a");
        String first = generator.forChat(ENDPOINT, MODEL, request);
        EndpointContext.getProcessData().setSessionId("session-b");
        String second = generator.forChat(ENDPOINT, MODEL, request);

        assertThat(first).isNotNull().isNotEqualTo(second);
    }

    @Test
    public void sameSessionIdProducesSameAffinityKeyForDifferentPromptContent() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        CompletionRequest first = CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder().role("system").content("first stable content").build()))
                .build();
        CompletionRequest second = CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder().role("system").content("different content xyz").build()))
                .build();

        EndpointContext.getProcessData().setSessionId("same-session");
        String firstKey = generator.forChat(ENDPOINT, MODEL, first);
        EndpointContext.getProcessData().setSessionId("same-session");
        String secondKey = generator.forChat(ENDPOINT, MODEL, second);

        assertThat(firstKey).isNotNull().isEqualTo(secondKey);
    }

    @Test
    public void explicitPromptCacheKeyBecomesSessionIdWithoutChangingRequest() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        CompletionRequest request = CompletionRequest.builder()
                .prompt_cache_key("body-cache-id")
                .messages(Collections.singletonList(Message.builder()
                        .role("system")
                        .content("stable content prefix abcdefghijklmnop")
                        .build()))
                .build();

        assertThat(generator.forChat(ENDPOINT, MODEL, request)).isNotNull();
        assertThat(request.getPrompt_cache_key()).isEqualTo("body-cache-id");
        assertThat(EndpointContext.getProcessData().getSessionId()).isEqualTo("body-cache-id");
    }

    @Test
    public void promptPrefixCreatesSessionIdUsedForAffinity() {
        RouteAffinityKeyGenerator generator = generator(true, 16);
        CompletionRequest request = CompletionRequest.builder()
                .messages(Collections.singletonList(Message.builder()
                        .role("system")
                        .content("stable content prefix abcdefghijklmnop")
                        .build()))
                .build();

        assertThat(generator.forChat(ENDPOINT, MODEL, request)).isNotNull();
        assertThat(EndpointContext.getProcessData().getSessionId()).matches("[0-9a-f]{64}");
    }

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

        assertThat(freshChat(generator, first))
                .isEqualTo(freshChat(generator, second));
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

        assertThat(freshChat(generator, first))
                .isNotNull()
                .isEqualTo(freshChat(generator, second));
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

        assertThat(freshMessages(generator, first))
                .isEqualTo(freshMessages(generator, second));
    }

    @Test
    public void forMessages_ignoresClaudeCodeAttributionInStringSystem() {
        RouteAffinityKeyGenerator generator = generator(true, 32);
        MessageRequest first = MessageRequest.builder()
                .system(attribution("aaa") + "\nStable prompt prefix abcdefghijklmnopqrstuvwxyz")
                .build();
        MessageRequest second = MessageRequest.builder()
                .system(attribution("bbb") + "\nStable prompt prefix abcdefghijklmnopqrstuvwxyz")
                .build();

        assertThat(freshMessages(generator, first))
                .isNotNull()
                .isEqualTo(freshMessages(generator, second));
    }

    @Test
    public void forMessages_ignoresClaudeCodeAttributionInFirstSystemBlock() {
        RouteAffinityKeyGenerator generator = generator(true, 32);
        MessageRequest first = MessageRequest.builder()
                .system(Arrays.asList(systemBlock(attribution("aaa")), systemBlock("Stable prompt prefix abcdefghijklmnopqrstuvwxyz")))
                .build();
        MessageRequest second = MessageRequest.builder()
                .system(Arrays.asList(systemBlock(attribution("bbb")), systemBlock("Stable prompt prefix abcdefghijklmnopqrstuvwxyz")))
                .build();

        assertThat(freshMessages(generator, first))
                .isNotNull()
                .isEqualTo(freshMessages(generator, second));
    }

    @Test
    public void forMessages_ignoresClaudeCodeAttributionWithoutTrailingSemicolon() {
        RouteAffinityKeyGenerator generator = generator(true, 32);
        MessageRequest first = MessageRequest.builder()
                .system(attributionWithoutTrailingSemicolon("aaa") + "\nStable prompt prefix abcdefghijklmnopqrstuvwxyz")
                .build();
        MessageRequest second = MessageRequest.builder()
                .system(attributionWithoutTrailingSemicolon("bbb") + "\nStable prompt prefix abcdefghijklmnopqrstuvwxyz")
                .build();

        assertThat(freshMessages(generator, first))
                .isNotNull()
                .isEqualTo(freshMessages(generator, second));
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

        assertThat(freshResponses(generator, first))
                .isEqualTo(freshResponses(generator, second));
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

        assertThat(freshResponses(generator, first))
                .isEqualTo(freshResponses(generator, second));
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

    private String attribution(String cch) {
        return "x-anthropic-billing-header: cc_version=2.1.153.2cc; cc_entrypoint=sdk-py; cch=" + cch + ";";
    }

    private String attributionWithoutTrailingSemicolon(String cch) {
        String attribution = attribution(cch);
        return attribution.substring(0, attribution.length() - 1);
    }

    private MessageRequest.RequestTextBlock systemBlock(String text) {
        return MessageRequest.RequestTextBlock.builder().type("text").text(text).build();
    }

    private String freshChat(RouteAffinityKeyGenerator generator, CompletionRequest request) {
        EndpointContext.clearAll();
        return generator.forChat(ENDPOINT, MODEL, request);
    }

    private String freshMessages(RouteAffinityKeyGenerator generator, MessageRequest request) {
        EndpointContext.clearAll();
        return generator.forMessages("/v1/messages", MODEL, request);
    }

    private String freshResponses(RouteAffinityKeyGenerator generator, ResponsesApiRequest request) {
        EndpointContext.clearAll();
        return generator.forResponses("/v1/responses", MODEL, request);
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
