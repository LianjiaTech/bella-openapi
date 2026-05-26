package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StreamMessagesCallbackTest {
    private static final String MODEL = "deepseek-v4-pro";

    @Test
    public void toolUseStartsAtIndexZeroAndDeltasUseSameIndex() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.callback(toolStartChunk(0, "call_1", "Read", ""));
        callback.callback(toolDeltaChunk(0, "{\"file_path\":\"/tmp/a\"}"));

        List<StreamMessageResponse> events = callback.events;
        assertEquals("message_start", events.get(0).getType());
        assertEquals("content_block_start", events.get(1).getType());
        assertEquals(0, events.get(1).getIndex().intValue());
        assertEquals("content_block_delta", events.get(2).getType());
        assertEquals(0, events.get(2).getIndex().intValue());
        assertEquals("content_block_delta", events.get(3).getType());
        assertEquals(0, events.get(3).getIndex().intValue());
    }

    @Test
    public void finishAfterToolUseDoesNotOpenEmptyContentBlock() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.callback(toolStartChunk(0, "call_1", "Read", ""));
        callback.callback(toolDeltaChunk(0, "{\"file_path\":\"/tmp/a\"}"));
        callback.callback(finishChunk("tool_calls"));
        callback.done();

        List<StreamMessageResponse> events = callback.events;
        long starts = events.stream().filter(event -> "content_block_start".equals(event.getType())).count();
        assertEquals(1, starts);
        assertTrue(events.stream().anyMatch(event -> "content_block_stop".equals(event.getType()) && event.getIndex() == 0));
        assertFalse(events.stream().anyMatch(event -> "content_block_start".equals(event.getType()) && event.getIndex() == 1));
        assertValidCompleteMessageStream(events);
    }

    @Test
    public void toolUseAfterTextAdvancesToNextContentBlock() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.callback(textChunk("I will inspect it."));
        callback.callback(toolStartChunk(0, "call_1", "Read", ""));

        List<StreamMessageResponse> events = callback.events;
        assertTrue(events.stream().anyMatch(event -> "content_block_start".equals(event.getType()) && event.getIndex() == 0));
        assertTrue(events.stream().anyMatch(event -> "content_block_stop".equals(event.getType()) && event.getIndex() == 0));
        assertTrue(events.stream().anyMatch(event -> "content_block_start".equals(event.getType()) && event.getIndex() == 1));
        assertTrue(events.stream().anyMatch(event -> "content_block_delta".equals(event.getType()) && event.getIndex() == 1));
    }

    @Test
    public void consecutiveToolUsesStopPreviousBlockBeforeStartingNextOne() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.callback(toolStartChunk(0, "call_1", "Read", ""));
        callback.callback(toolDeltaChunk(0, "{\"file_path\":\"/tmp/a\"}"));
        callback.callback(toolStartChunk(1, "call_2", "Write", ""));
        callback.callback(toolDeltaChunk(1, "{\"file_path\":\"/tmp/b\"}"));
        callback.callback(finishChunk("tool_calls"));
        callback.done();

        List<StreamMessageResponse> events = callback.events;
        assertEvent(events.get(1), "content_block_start", 0);
        assertEvent(events.get(4), "content_block_stop", 0);
        assertEvent(events.get(5), "content_block_start", 1);
        assertEvent(events.get(6), "content_block_delta", 1);
        assertTrue(events.stream().noneMatch(event -> "content_block_stop".equals(event.getType()) && event.getIndex() < 0));
        assertValidCompleteMessageStream(events);
    }

    @Test
    public void finishWithoutContentDoesNotEmitNegativeContentBlockStop() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.callback(finishChunk("stop"));
        callback.done();

        List<StreamMessageResponse> events = callback.events;
        assertTrue(events.stream().noneMatch(event -> "content_block_start".equals(event.getType())));
        assertTrue(events.stream().noneMatch(event -> "content_block_stop".equals(event.getType())));
        assertTrue(events.stream().anyMatch(event -> "message_delta".equals(event.getType())));
        assertValidCompleteMessageStream(events);
    }

    @Test
    public void doneWithoutPriorContentDoesNotEmitNegativeContentBlockStop() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.done();

        List<StreamMessageResponse> events = callback.events;
        assertTrue(events.stream().noneMatch(event -> "content_block_stop".equals(event.getType())));
        assertTrue(events.stream().anyMatch(event -> "message_delta".equals(event.getType())));
    }

    @Test
    public void emptyDeltaBeforeFinishDoesNotOpenEmptyTextBlock() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.callback(emptyTextChunk());
        callback.callback(finishChunk("stop"));
        callback.done();

        List<StreamMessageResponse> events = callback.events;
        assertTrue(events.stream().noneMatch(event -> "content_block_start".equals(event.getType())));
        assertTrue(events.stream().noneMatch(event -> "content_block_stop".equals(event.getType())));
        assertValidCompleteMessageStream(events);
    }

    @Test
    public void emptyTextDeltaBeforeNormalTextDoesNotOpenExtraBlock() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.callback(emptyTextChunk());
        callback.callback(textChunk("hello"));
        callback.callback(finishChunk("stop"));
        callback.done();

        List<StreamMessageResponse> events = callback.events;
        assertEvent(events.get(1), "content_block_start", 0);
        assertEvent(events.get(2), "content_block_delta", 0);
        assertEvent(events.get(3), "content_block_stop", 0);
        assertTrue(events.stream().noneMatch(event -> "content_block_start".equals(event.getType()) && event.getIndex() == 1));
        assertValidCompleteMessageStream(events);
    }

    @Test
    public void thinkingThenTextClosesThinkingBlockBeforeTextBlock() {
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback();

        callback.callback(thinkingChunk("let me think"));
        callback.callback(textChunk("answer"));
        callback.callback(finishChunk("stop"));
        callback.done();

        List<StreamMessageResponse> events = callback.events;
        assertEvent(events.get(1), "content_block_start", 0);
        assertTrue(events.get(1).getContentBlock() instanceof MessageResponse.ResponseThinkingBlock);
        assertEvent(events.get(2), "content_block_delta", 0);
        assertEvent(events.get(3), "content_block_stop", 0);
        assertEvent(events.get(4), "content_block_start", 1);
        assertTrue(events.get(4).getContentBlock() instanceof MessageResponse.ResponseTextBlock);
        assertEvent(events.get(5), "content_block_delta", 1);
        assertEvent(events.get(6), "content_block_stop", 1);
        assertValidCompleteMessageStream(events);
    }

    private static StreamCompletionResponse textChunk(String text) {
        return streamResponse(Message.builder()
                .content(text)
                .build(), null);
    }

    private static StreamCompletionResponse thinkingChunk(String thinking) {
        return streamResponse(Message.builder()
                .reasoning_content(thinking)
                .build(), null);
    }

    private static StreamCompletionResponse emptyTextChunk() {
        return textChunk("");
    }

    private static StreamCompletionResponse toolStartChunk(int index, String id, String name, String arguments) {
        return streamResponse(Message.builder()
                .tool_calls(Collections.singletonList(Message.ToolCall.builder()
                        .index(index)
                        .id(id)
                        .type("function")
                        .function(Message.FunctionCall.builder()
                                .name(name)
                                .arguments(arguments)
                                .build())
                        .build()))
                .build(), null);
    }

    private static StreamCompletionResponse toolDeltaChunk(int index, String arguments) {
        return streamResponse(Message.builder()
                .tool_calls(Collections.singletonList(Message.ToolCall.builder()
                        .index(index)
                        .function(Message.FunctionCall.builder()
                                .arguments(arguments)
                                .build())
                        .build()))
                .build(), null);
    }

    private static StreamCompletionResponse finishChunk(String finishReason) {
        CompletionResponse.TokenUsage usage = new CompletionResponse.TokenUsage();
        usage.setPrompt_tokens(10);
        usage.setCompletion_tokens(2);
        usage.setTotal_tokens(12);
        return streamResponse(Message.builder().build(), finishReason, usage);
    }

    private static StreamCompletionResponse streamResponse(Message delta, String finishReason) {
        return streamResponse(delta, finishReason, null);
    }

    private static StreamCompletionResponse streamResponse(Message delta, String finishReason, CompletionResponse.TokenUsage usage) {
        return StreamCompletionResponse.builder()
                .id("as_test")
                .model(MODEL)
                .usage(usage)
                .choices(Collections.singletonList(StreamCompletionResponse.Choice.builder()
                        .index(0)
                        .delta(delta)
                        .finish_reason(finishReason)
                        .build()))
                .build();
    }

    private static void assertEvent(StreamMessageResponse event, String type, int index) {
        assertEquals(type, event.getType());
        assertNotNull(event.getIndex());
        assertEquals(index, event.getIndex().intValue());
    }

    private static void assertValidCompleteMessageStream(List<StreamMessageResponse> events) {
        Map<Integer, String> openBlocks = new HashMap<>();
        int nextBlockIndex = 0;
        for (StreamMessageResponse event : events) {
            if("content_block_start".equals(event.getType())) {
                assertNotNull("content_block_start 必须带 index", event.getIndex());
                assertEquals("content_block index 必须连续递增", nextBlockIndex, event.getIndex().intValue());
                openBlocks.put(event.getIndex(), contentBlockType(event.getContentBlock()));
                nextBlockIndex++;
            } else if("content_block_delta".equals(event.getType())) {
                assertNotNull("content_block_delta 必须带 index", event.getIndex());
                assertTrue("delta 必须落在已打开的 content_block 上: " + event.getIndex(), openBlocks.containsKey(event.getIndex()));
                assertDeltaMatchesBlock(openBlocks.get(event.getIndex()), event.getDelta());
            } else if("content_block_stop".equals(event.getType())) {
                assertNotNull("content_block_stop 必须带 index", event.getIndex());
                assertTrue("stop 必须关闭已打开的 content_block: " + event.getIndex(), openBlocks.containsKey(event.getIndex()));
                openBlocks.remove(event.getIndex());
            } else if("message_delta".equals(event.getType()) || "message_stop".equals(event.getType())) {
                assertTrue(event.getType() + " 前必须关闭所有 content_block", openBlocks.isEmpty());
            }
        }
        assertTrue("流结束后不能遗留未关闭的 content_block", openBlocks.isEmpty());
    }

    private static String contentBlockType(MessageResponse.ContentBlock block) {
        if(block instanceof MessageResponse.ResponseToolUseBlock) {
            return "tool_use";
        }
        if(block instanceof MessageResponse.ResponseTextBlock) {
            return "text";
        }
        if(block instanceof MessageResponse.ResponseThinkingBlock) {
            return "thinking";
        }
        return "unknown";
    }

    private static void assertDeltaMatchesBlock(String blockType, Object delta) {
        if(delta instanceof StreamMessageResponse.InputJsonDelta) {
            assertEquals("input_json_delta 只能属于 tool_use block", "tool_use", blockType);
        } else if(delta instanceof StreamMessageResponse.TextDelta) {
            assertEquals("text_delta 只能属于 text block", "text", blockType);
        } else if(delta instanceof StreamMessageResponse.ThinkingDelta
                || delta instanceof StreamMessageResponse.SignatureDelta
                || delta instanceof StreamMessageResponse.RedactedThinkingDelta) {
            assertEquals("thinking delta 只能属于 thinking block", "thinking", blockType);
        }
    }

    private static class CollectingStreamMessagesCallback extends StreamMessagesCallback {
        private final List<StreamMessageResponse> events = new ArrayList<>();

        CollectingStreamMessagesCallback() {
            super(null, processData(), null, null, null);
        }

        @Override
        public void send(Object data) {
            events.add((StreamMessageResponse) data);
        }

        private static EndpointProcessData processData() {
            EndpointProcessData processData = new EndpointProcessData();
            processData.setModel(MODEL);
            return processData;
        }
    }
}
