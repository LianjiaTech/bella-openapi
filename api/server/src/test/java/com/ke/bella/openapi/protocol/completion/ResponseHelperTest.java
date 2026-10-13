package com.ke.bella.openapi.protocol.completion;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ResponseHelperTest {

    @Test
    public void combineMessageAggregatesToolCallsWhenContentIsPresent() {
        Message target = ResponseHelper.combineMessage(null, Message.builder()
                .content("")
                .tool_calls(Collections.singletonList(toolCall(0, "call_1", "function", "get_weather", "{\"city\":\"Bei")))
                .build());

        ResponseHelper.combineMessage(target, Message.builder()
                .content("")
                .tool_calls(Collections.singletonList(toolCall(0, null, null, null, "jing\"}")))
                .build());

        assertEquals("", target.getContent());
        assertNotNull(target.getTool_calls());
        assertEquals(1, target.getTool_calls().size());
        Message.ToolCall toolCall = target.getTool_calls().get(0);
        assertEquals(0, toolCall.getIndex());
        assertEquals("call_1", toolCall.getId());
        assertEquals("function", toolCall.getType());
        assertEquals("get_weather", toolCall.getFunction().getName());
        assertEquals("{\"city\":\"Beijing\"}", toolCall.getFunction().getArguments());
    }

    @Test
    public void combineMessageAggregatesMultipleToolCallArgumentsByIndex() {
        Message target = ResponseHelper.combineMessage(null, Message.builder()
                .content("checking")
                .tool_calls(Arrays.asList(
                        toolCall(0, "call_1", "function", "get_weather", "{\"city\":\"Bei"),
                        toolCall(1, "call_2", "function", "get_time", "{\"zone\":\"UT")))
                .build());

        ResponseHelper.combineMessage(target, Message.builder()
                .tool_calls(Arrays.asList(
                        toolCall(0, null, null, null, "jing\"}"),
                        toolCall(1, null, null, null, "C\"}")))
                .build());

        assertEquals("checking", target.getContent());
        assertEquals(2, target.getTool_calls().size());
        assertEquals("{\"city\":\"Beijing\"}", target.getTool_calls().get(0).getFunction().getArguments());
        assertEquals("{\"zone\":\"UTC\"}", target.getTool_calls().get(1).getFunction().getArguments());
    }

    @Test
    public void combineMessageAggregatesReasoningContentAndToolCallsIndependently() {
        Message target = ResponseHelper.combineMessage(null, Message.builder()
                .reasoning_content("need a tool")
                .tool_calls(Collections.singletonList(toolCall(0, "call_1", "function", "search", "{\"q\":\"")))
                .build());

        ResponseHelper.combineMessage(target, Message.builder()
                .reasoning_content(" now")
                .tool_calls(Collections.singletonList(toolCall(0, null, null, null, "hello\"}")))
                .build());

        assertEquals("need a tool now", target.getReasoning_content());
        assertEquals("{\"q\":\"hello\"}", target.getTool_calls().get(0).getFunction().getArguments());
    }

    private static Message.ToolCall toolCall(int index, String id, String type, String name, String arguments) {
        return Message.ToolCall.builder()
                .index(index)
                .id(id)
                .type(type)
                .function(Message.FunctionCall.builder()
                        .name(name)
                        .arguments(arguments)
                        .build())
                .build();
    }
}
