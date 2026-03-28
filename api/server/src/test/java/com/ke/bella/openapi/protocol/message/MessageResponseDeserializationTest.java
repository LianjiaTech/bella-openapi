package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 测试 MessageResponse / StreamMessageResponse 的序列化与反序列化
 *
 * 覆盖场景：
 * 1. MessageResponse 基本反序列化（input_tokens 字段映射）
 * 2. 含 tool_use 的 MessageResponse 反序列化
 * 3. getTotalInputTokens 计算（含 cache tokens）
 * 4. MessageResponse.Usage 序列化字段名验证
 * 5. StreamUsage 普通场景反序列化（message_delta 仅含 output_tokens，缺失字段默认为 0）
 * 6. StreamUsage 普通场景序列化（int 字段 0 值会被输出）
 * 7. StreamUsage tool_use 场景反序列化（message_delta 含完整 usage）
 * 8. AwsMessageAdaptor 补齐逻辑验证：inputTokens==0 时从 message_start 补入
 * 9. AwsMessageAdaptor 补齐逻辑验证：tool_use 场景 inputTokens>0 直接透传
 */
public class MessageResponseDeserializationTest {

    /**
     * 场景1: 普通文本响应反序列化
     * 验证 usage.input_tokens 能正确映射到 inputTokens 字段
     */
    @Test
    public void testBasicTextResponseDeserialization() {
        String json = "{"
                + "\"id\":\"msg_01\","
                + "\"type\":\"message\","
                + "\"role\":\"assistant\","
                + "\"model\":\"claude-opus-4-6\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Hello\"}],"
                + "\"stop_reason\":\"end_turn\","
                + "\"stop_sequence\":null,"
                + "\"usage\":{"
                + "  \"input_tokens\":25,"
                + "  \"output_tokens\":15,"
                + "  \"cache_creation_input_tokens\":0,"
                + "  \"cache_read_input_tokens\":0"
                + "}"
                + "}";

        MessageResponse response = JacksonUtils.deserialize(json, MessageResponse.class);

        assertNotNull("反序列化结果不应为 null", response);
        assertEquals("msg_01", response.getId());
        assertEquals("end_turn", response.getStopReason());

        // 核心验证：input_tokens 正确映射
        assertNotNull("usage 不应为 null", response.getUsage());
        assertEquals("input_tokens 应正确反序列化", 25, response.getUsage().getInputTokens());
        assertEquals("output_tokens 应正确反序列化", 15, response.getUsage().getOutputTokens());

        // content 正确反序列化
        assertNotNull(response.getContent());
        assertEquals(1, response.getContent().size());
        assertTrue(response.getContent().get(0) instanceof MessageResponse.ResponseTextBlock);
        assertEquals("Hello", ((MessageResponse.ResponseTextBlock) response.getContent().get(0)).getText());
    }

    /**
     * 场景2: 含 tool_use 的响应反序列化
     * 验证 tool_use content block 能正确反序列化
     */
    @Test
    public void testToolUseResponseDeserialization() {
        String json = "{"
                + "\"id\":\"msg_02\","
                + "\"type\":\"message\","
                + "\"role\":\"assistant\","
                + "\"model\":\"glm-5\","
                + "\"content\":["
                + "  {\"type\":\"text\",\"text\":\"现在构建项目：\"},"
                + "  {"
                + "    \"type\":\"tool_use\","
                + "    \"id\":\"call_56c40127\","
                + "    \"name\":\"mcp__husky__Bash\","
                + "    \"input\":{\"command\":\"npm run build\",\"timeout\":80}"
                + "  }"
                + "],"
                + "\"stop_reason\":\"tool_use\","
                + "\"stop_sequence\":null,"
                + "\"usage\":{"
                + "  \"input_tokens\":45,"
                + "  \"output_tokens\":72,"
                + "  \"cache_creation_input_tokens\":0,"
                + "  \"cache_read_input_tokens\":31872"
                + "}"
                + "}";

        MessageResponse response = JacksonUtils.deserialize(json, MessageResponse.class);

        assertNotNull("含 tool_use 的响应反序列化不应为 null", response);
        assertEquals("tool_use", response.getStopReason());

        List<MessageResponse.ContentBlock> content = response.getContent();
        assertNotNull(content);
        assertEquals(2, content.size());

        assertTrue("第一个 block 应为 text", content.get(0) instanceof MessageResponse.ResponseTextBlock);
        assertTrue("第二个 block 应为 tool_use", content.get(1) instanceof MessageResponse.ResponseToolUseBlock);

        MessageResponse.ResponseToolUseBlock toolUse = (MessageResponse.ResponseToolUseBlock) content.get(1);
        assertEquals("call_56c40127", toolUse.getId());
        assertEquals("mcp__husky__Bash", toolUse.getName());
        assertEquals("npm run build", toolUse.getInput().get("command"));

        // 验证 usage
        assertEquals(45, response.getUsage().getInputTokens());
        assertEquals(72, response.getUsage().getOutputTokens());
        assertEquals(31872, response.getUsage().getCacheReadInputTokens());
    }

    /**
     * 场景3: getTotalInputTokens 计算正确（含 cache tokens）
     */
    @Test
    public void testGetTotalInputTokens() {
        String json = "{"
                + "\"id\":\"msg_03\","
                + "\"type\":\"message\","
                + "\"role\":\"assistant\","
                + "\"model\":\"claude-opus-4-6\","
                + "\"content\":[],"
                + "\"stop_reason\":\"end_turn\","
                + "\"usage\":{"
                + "  \"input_tokens\":100,"
                + "  \"output_tokens\":50,"
                + "  \"cache_creation_input_tokens\":200,"
                + "  \"cache_read_input_tokens\":300"
                + "}"
                + "}";

        MessageResponse response = JacksonUtils.deserialize(json, MessageResponse.class);
        assertNotNull(response);

        MessageResponse.Usage usage = response.getUsage();
        assertEquals("原始 input_tokens", 100, usage.getInputTokens());
        assertEquals("cache_creation_input_tokens", 200, usage.getCacheCreationInputTokens());
        assertEquals("cache_read_input_tokens", 300, usage.getCacheReadInputTokens());
        assertEquals("getTotalInputTokens 应为三者之和", 600, usage.getTotalInputTokens());
    }

    /**
     * 场景4: 序列化输出 input_tokens 字段名正确
     */
    @Test
    public void testUsageSerialization() {
        MessageResponse.Usage usage = MessageResponse.Usage.builder()
                .inputTokens(100)
                .outputTokens(50)
                .cacheCreationInputTokens(0)
                .cacheReadInputTokens(0)
                .build();

        String json = JacksonUtils.serialize(usage);
        assertTrue("序列化后应包含 input_tokens 字段", json.contains("\"input_tokens\":100"));
        assertTrue("序列化后应包含 output_tokens 字段", json.contains("\"output_tokens\":50"));
    }

    // ==================== StreamUsage 序列化/反序列化测试 ====================

    /**
     * 场景5: 普通对话的 message_delta 反序列化
     * 原生协议中 message_delta 的 usage 仅含 output_tokens，其余字段不存在。
     * StreamUsage 字段为 int 类型，缺失字段反序列化为默认值 0。
     * AwsMessageAdaptor 以 inputTokens==0 作为"需要补齐"的判断依据。
     */
    @Test
    public void testStreamUsage_normalScenario_missingFieldsDefaultToZero() {
        // 原生 Anthropic message_delta 的 usage：只有 output_tokens
        String json = "{\"type\":\"message_delta\","
                + "\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},"
                + "\"usage\":{\"output_tokens\":15}}";

        StreamMessageResponse response = JacksonUtils.deserialize(json, StreamMessageResponse.class);

        assertNotNull(response);
        assertNotNull(response.getUsage());
        StreamMessageResponse.StreamUsage usage = response.getUsage();

        assertEquals("output_tokens 应正确反序列化", 15, usage.getOutputTokens());
        // int 类型缺失字段默认为 0，AwsMessageAdaptor 用 inputTokens==0 判断需要补齐
        assertEquals("input_tokens 缺失时默认为 0，触发补齐逻辑", 0, usage.getInputTokens());
        assertEquals("cache_creation_input_tokens 缺失时默认为 0", 0, usage.getCacheCreationInputTokens());
        assertEquals("cache_read_input_tokens 缺失时默认为 0", 0, usage.getCacheReadInputTokens());
    }

    /**
     * 场景6: 补齐后的 StreamUsage 序列化
     * 补齐了 input_tokens 后，序列化应正确输出所有字段。
     */
    @Test
    public void testStreamUsage_patchedUsage_serialization() {
        StreamMessageResponse.StreamUsage patchedUsage = StreamMessageResponse.StreamUsage.builder()
                .inputTokens(25)
                .outputTokens(15)
                .cacheCreationInputTokens(0)
                .cacheReadInputTokens(0)
                .build();

        String json = JacksonUtils.serialize(patchedUsage);

        assertTrue("应包含补齐后的 input_tokens", json.contains("\"input_tokens\":25"));
        assertTrue("应包含 output_tokens", json.contains("\"output_tokens\":15"));
    }

    /**
     * 场景7: tool_use 场景的 message_delta 反序列化
     * 使用 web_search 等 server tool 时，上游会在 message_delta 中提供完整 usage，
     * input_tokens > 0，AwsMessageAdaptor 直接透传不补齐。
     */
    @Test
    public void testStreamUsage_toolUseScenario_deserializesFullUsage() {
        // tool_use 场景下上游给出的完整 message_delta
        String json = "{\"type\":\"message_delta\","
                + "\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},"
                + "\"usage\":{"
                + "  \"input_tokens\":10682,"
                + "  \"cache_creation_input_tokens\":0,"
                + "  \"cache_read_input_tokens\":0,"
                + "  \"output_tokens\":510"
                + "}}";

        StreamMessageResponse response = JacksonUtils.deserialize(json, StreamMessageResponse.class);

        assertNotNull(response);
        StreamMessageResponse.StreamUsage usage = response.getUsage();
        assertNotNull(usage);

        assertEquals("input_tokens 应正确反序列化", 10682, usage.getInputTokens());
        assertEquals("output_tokens 应正确反序列化", 510, usage.getOutputTokens());
        // input_tokens > 0，不会触发补齐逻辑
        assertTrue("input_tokens > 0 时不应触发补齐", usage.getInputTokens() > 0);
    }

    // ==================== AwsMessageAdaptor 补齐逻辑验证 ====================

    /**
     * 场景8: 普通场景补齐逻辑验证
     * message_delta.usage.inputTokens == 0 时，应从 message_start 缓存的 usage 中补入。
     */
    @Test
    public void testPatchLogic_normalScenario_inputTokensPatched() {
        // message_start 缓存的 usage
        MessageResponse.Usage messageStartUsage = MessageResponse.Usage.builder()
                .inputTokens(25)
                .outputTokens(1)
                .cacheCreationInputTokens(100)
                .cacheReadInputTokens(200)
                .build();

        // 上游 message_delta：只有 output_tokens，int 默认 inputTokens=0
        StreamMessageResponse.StreamUsage deltaUsage = StreamMessageResponse.StreamUsage.builder()
                .outputTokens(15)
                .build();

        // 模拟 AwsMessageAdaptor 的补齐判断：inputTokens == 0 时补入
        boolean shouldPatch = deltaUsage.getInputTokens() == 0;
        assertTrue("inputTokens==0 时应触发补齐", shouldPatch);

        StreamMessageResponse.StreamUsage patchedUsage = StreamMessageResponse.StreamUsage.builder()
                .inputTokens(messageStartUsage.getInputTokens())
                .outputTokens(deltaUsage.getOutputTokens())
                .cacheCreationInputTokens(messageStartUsage.getCacheCreationInputTokens())
                .cacheReadInputTokens(messageStartUsage.getCacheReadInputTokens())
                .build();

        assertEquals("补齐后 input_tokens 应来自 message_start", 25, patchedUsage.getInputTokens());
        assertEquals("补齐后 output_tokens 应来自 message_delta", 15, patchedUsage.getOutputTokens());
        assertEquals("补齐后 cache_creation_input_tokens 应来自 message_start", 100, patchedUsage.getCacheCreationInputTokens());
        assertEquals("补齐后 cache_read_input_tokens 应来自 message_start", 200, patchedUsage.getCacheReadInputTokens());

        String json = JacksonUtils.serialize(patchedUsage);
        assertFalse("补齐后 input_tokens 应为真实值，不应为 0", json.contains("\"input_tokens\":0"));
        assertTrue("补齐后应输出真实 input_tokens", json.contains("\"input_tokens\":25"));
    }

    /**
     * 场景9: tool_use 场景透传逻辑验证
     * message_delta.usage.inputTokens > 0 时，说明上游已提供完整 token 信息，
     * 直接透传，不应用 message_start 的旧值覆盖。
     */
    @Test
    public void testPatchLogic_toolUseScenario_noOverwrite() {
        // 上游 message_delta：已含完整 usage（web search 后 input_tokens 有更新）
        StreamMessageResponse.StreamUsage deltaUsage = StreamMessageResponse.StreamUsage.builder()
                .inputTokens(10682)
                .outputTokens(510)
                .cacheCreationInputTokens(0)
                .cacheReadInputTokens(0)
                .build();

        // 模拟 AwsMessageAdaptor 的判断逻辑：inputTokens > 0 时不补齐，直接透传
        boolean shouldPatch = deltaUsage.getInputTokens() == 0;
        assertFalse("上游已提供 input_tokens（>0），不应触发补齐", shouldPatch);

        // 直接透传，input_tokens 保留上游的值
        assertEquals("透传时 input_tokens 应保留上游值 10682", 10682, deltaUsage.getInputTokens());
        assertEquals("透传时 output_tokens 应保留上游值 510", 510, deltaUsage.getOutputTokens());
    }

    /**
     * 场景10: StreamUsage.getTotalInputTokens 计算验证
     */
    @Test
    public void testStreamUsage_getTotalInputTokens() {
        // 普通场景补齐后：有真实值
        StreamMessageResponse.StreamUsage usage = StreamMessageResponse.StreamUsage.builder()
                .inputTokens(25)
                .outputTokens(15)
                .cacheCreationInputTokens(100)
                .cacheReadInputTokens(200)
                .build();

        assertEquals("totalInputTokens 应为 input + cache_creation + cache_read = 325",
                325, usage.getTotalInputTokens());

        // 未补齐时：全为 0
        StreamMessageResponse.StreamUsage emptyUsage = StreamMessageResponse.StreamUsage.builder()
                .outputTokens(15)
                .build();

        assertEquals("未补齐时 totalInputTokens 应为 0", 0, emptyUsage.getTotalInputTokens());
    }
}