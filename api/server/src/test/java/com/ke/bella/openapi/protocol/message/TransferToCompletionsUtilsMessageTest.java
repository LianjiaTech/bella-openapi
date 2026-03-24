package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * 测试 TransferToCompletionsUtils 中 message 协议转换逻辑
 * 核心场景：message_delta.usage 的 input_tokens 处理
 *
 * 标准协议规定 message_delta.usage 是累计值，但有两种情况：
 * 1. 普通对话：message_delta 只含 output_tokens，input_tokens=0，需从 message_start 补充
 * 2. 含 server tool（如 web search）：message_delta 含完整累计 input_tokens，直接用
 * 3. 非标协议（如 glm）：message_start 全 0，message_delta 含完整 usage，直接用
 */
public class TransferToCompletionsUtilsMessageTest {

    private static final String MODEL = "claude-opus-4-6";
    private static final String ID = "msg_test_01";
    private AtomicInteger toolNum;

    // message_start 保存的 usage（标准协议下有真实 input_tokens）
    private MessageResponse.Usage messageStartUsage;

    @Before
    public void setUp() {
        toolNum = new AtomicInteger(0);
        messageStartUsage = MessageResponse.Usage.builder()
                .inputTokens(1000)
                .outputTokens(1)
                .cacheCreationInputTokens(0)
                .cacheReadInputTokens(500)
                .build();
    }

    // ==================== 非流式转换测试 ====================

    /**
     * 非流式：prompt_tokens 应等于 totalInputTokens（含 cache）
     */
    @Test
    public void testConvertResponse_promptTokensIncludesCache() {
        MessageResponse messageResponse = buildMessageResponse(100, 50, 200, 300);

        CompletionResponse result = TransferToCompletionsUtils.convertResponse(messageResponse);

        assertNotNull(result);
        assertNotNull(result.getUsage());
        // prompt_tokens = input(100) + cacheCreation(200) + cacheRead(300) = 600
        assertEquals("prompt_tokens 应包含 cache tokens", 600, result.getUsage().getPrompt_tokens());
        assertEquals("completion_tokens 正确", 50, result.getUsage().getCompletion_tokens());
        assertEquals("total_tokens 正确", 650, result.getUsage().getTotal_tokens());
        assertEquals("cache_creation_tokens 单独保留", 200, result.getUsage().getCache_creation_tokens());
        assertEquals("cache_read_tokens 单独保留", 300, result.getUsage().getCache_read_tokens());
    }

    /**
     * 非流式：无 cache 时 prompt_tokens_details 为 null
     */
    @Test
    public void testConvertResponse_noCache() {
        MessageResponse messageResponse = buildMessageResponse(500, 100, 0, 0);

        CompletionResponse result = TransferToCompletionsUtils.convertResponse(messageResponse);

        assertNotNull(result);
        assertEquals(500, result.getUsage().getPrompt_tokens());
        assertNull("无 cache 时 prompt_tokens_details 应为 null", result.getUsage().getPrompt_tokens_details());
    }

    // ==================== 流式转换测试 ====================

    /**
     * 场景1：普通对话 - message_delta 只有 output_tokens，input_tokens=0
     * 期望：从 message_start 的 tokenUsage 补充 input_tokens
     */
    @Test
    public void testConvertStreamResponse_normalChat_inputFromMessageStart() {
        // 标准 Anthropic 普通对话：message_delta 只有 output_tokens
        StreamMessageResponse messageDelta = buildStreamMessageDelta(0, 15, 0, 0);

        StreamCompletionResponse result = TransferToCompletionsUtils.convertStreamResponse(
                messageDelta, MODEL, ID, toolNum, messageStartUsage);

        assertNotNull(result);
        assertNotNull(result.getUsage());
        // input_tokens 从 message_start 补充：1000 + 0 + 500 = 1500
        assertEquals("普通对话 prompt_tokens 应从 message_start 补充", 1500, result.getUsage().getPrompt_tokens());
        assertEquals("completion_tokens 直接用 message_delta 的值", 15, result.getUsage().getCompletion_tokens());
        assertEquals("cache_read_tokens 从 message_start 补充", 500, result.getUsage().getCache_read_tokens());
    }

    /**
     * 场景2：含 server tool - message_delta 有完整累计 input_tokens
     * 期望：直接用 message_delta 的值，不叠加 message_start
     */
    @Test
    public void testConvertStreamResponse_serverTool_inputFromMessageDelta() {
        // 含 web_search 等 server tool：message_delta 有完整累计 usage
        StreamMessageResponse messageDelta = buildStreamMessageDelta(10682, 510, 0, 0);

        StreamCompletionResponse result = TransferToCompletionsUtils.convertStreamResponse(
                messageDelta, MODEL, ID, toolNum, messageStartUsage);

        assertNotNull(result);
        // 直接用 message_delta 的累计值，不叠加 message_start
        assertEquals("server tool 场景直接用 message_delta 的 input_tokens", 10682, result.getUsage().getPrompt_tokens());
        assertEquals("completion_tokens 直接用 message_delta 的值", 510, result.getUsage().getCompletion_tokens());
    }

    /**
     * 场景3：非标协议（glm）- message_start 全 0，message_delta 有完整 usage
     * 期望：直接用 message_delta 的值
     */
    @Test
    public void testConvertStreamResponse_glmNonStandard_inputFromMessageDelta() {
        // glm：message_start 全 0
        MessageResponse.Usage glmMessageStartUsage = MessageResponse.Usage.builder()
                .inputTokens(0).outputTokens(0)
                .cacheCreationInputTokens(0).cacheReadInputTokens(0)
                .build();

        // glm：message_delta 有完整 usage
        StreamMessageResponse messageDelta = buildStreamMessageDelta(31789, 68, 0, 128);

        StreamCompletionResponse result = TransferToCompletionsUtils.convertStreamResponse(
                messageDelta, MODEL, ID, toolNum, glmMessageStartUsage);

        assertNotNull(result);
        // prompt_tokens = 31789 + 0 + 128 = 31917
        assertEquals("glm 场景 prompt_tokens 直接用 message_delta 的完整值", 31917, result.getUsage().getPrompt_tokens());
        assertEquals("completion_tokens 正确", 68, result.getUsage().getCompletion_tokens());
        assertEquals("cache_read_tokens 正确", 128, result.getUsage().getCache_read_tokens());
        assertNotNull("有 cache 时应有 prompt_tokens_details", result.getUsage().getPrompt_tokens_details());
        assertEquals(128, (int) result.getUsage().getPrompt_tokens_details().getCached_tokens());
    }

    /**
     * 场景4：tokenUsage 为 null 时（无 message_start），message_delta input=0 退化为 0
     */
    @Test
    public void testConvertStreamResponse_nullTokenUsage() {
        StreamMessageResponse messageDelta = buildStreamMessageDelta(0, 20, 0, 0);

        StreamCompletionResponse result = TransferToCompletionsUtils.convertStreamResponse(
                messageDelta, MODEL, ID, toolNum, null);

        assertNotNull(result);
        assertEquals("tokenUsage 为 null 时 prompt_tokens 为 0", 0, result.getUsage().getPrompt_tokens());
        assertEquals("completion_tokens 正确", 20, result.getUsage().getCompletion_tokens());
    }

    /**
     * 场景5：output_tokens 不叠加（message_delta 是累计值，直接用，不加 message_start 的预占位值）
     */
    @Test
    public void testConvertStreamResponse_outputTokensNotAccumulated() {
        // message_start.output_tokens = 1（预占位）
        // message_delta.output_tokens = 15（最终累计值）
        // 期望：completion_tokens = 15，不是 1+15=16
        StreamMessageResponse messageDelta = buildStreamMessageDelta(0, 15, 0, 0);

        StreamCompletionResponse result = TransferToCompletionsUtils.convertStreamResponse(
                messageDelta, MODEL, ID, toolNum, messageStartUsage);

        assertEquals("output_tokens 不应叠加 message_start 的预占位值", 15, result.getUsage().getCompletion_tokens());
    }

    // ==================== 工具方法 ====================

    private MessageResponse buildMessageResponse(int inputTokens, int outputTokens,
            int cacheCreation, int cacheRead) {
        MessageResponse.Usage usage = MessageResponse.Usage.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .cacheCreationInputTokens(cacheCreation)
                .cacheReadInputTokens(cacheRead)
                .build();
        return MessageResponse.builder()
                .id(ID)
                .type("message")
                .role("assistant")
                .model(MODEL)
                .stopReason("end_turn")
                .content(Collections.emptyList())
                .usage(usage)
                .build();
    }

    private StreamMessageResponse buildStreamMessageDelta(int inputTokens, int outputTokens,
            int cacheCreation, int cacheRead) {
        StreamMessageResponse.StreamUsage streamUsage = StreamMessageResponse.StreamUsage.builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .cacheCreationInputTokens(cacheCreation)
                .cacheReadInputTokens(cacheRead)
                .build();
        return StreamMessageResponse.messageDelta(
                StreamMessageResponse.MessageDeltaInfo.builder()
                        .stopReason("end_turn")
                        .build(),
                streamUsage);
    }
}