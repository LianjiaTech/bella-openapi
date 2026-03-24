package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 测试 MessageResponse 反序列化
 * 核心修复：MessageResponse.Usage.inputTokens 字段缺少 @JsonProperty("input_tokens") 导致反序列化失败
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
}