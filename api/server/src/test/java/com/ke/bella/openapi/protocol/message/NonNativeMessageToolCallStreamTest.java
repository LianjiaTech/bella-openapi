package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.ResponseHelper;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.PayloadPart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NonNativeMessageToolCallStreamTest {
    private static final String MODEL = "GLM-5.3";

    @Test
    public void anthropicListenerKeepsParallelToolIndexesForChatCompletionConversion() {
        CapturingCallback callback = new CapturingCallback();
        AnthropicAdaptor.AnthropicStreamListener listener = new AnthropicAdaptor.AnthropicStreamListener(
                callback, MODEL, false);

        for(StreamMessageResponse event : events()) {
            listener.onEvent(null, null, event.getType(), JacksonUtils.serialize(event));
        }

        assertConvertedToolCalls(callback.responses);
    }

    @Test
    public void awsListenerKeepsParallelToolIndexesForChatCompletionConversion() {
        CapturingCallback callback = new CapturingCallback();
        AwsMessageAdaptor.AwsSseCompletionCallBack listener = new AwsMessageAdaptor.AwsSseCompletionCallBack(
                callback, false, MODEL, null, null, null, null);

        for(StreamMessageResponse event : events()) {
            listener.visitChunk(PayloadPart.builder()
                    .bytes(SdkBytes.fromUtf8String(JacksonUtils.serialize(event)))
                    .build());
        }

        assertConvertedToolCalls(callback.responses);
    }

    private static void assertConvertedToolCalls(List<StreamCompletionResponse> responses) {
        List<Message.ToolCall> toolChunks = new ArrayList<>();
        Message message = null;
        for(StreamCompletionResponse response : responses) {
            assertEquals(1, response.getChoices().size());
            assertEquals(0, response.getChoices().get(0).getIndex());
            Message delta = response.getChoices().get(0).getDelta();
            if(delta == null || delta.getTool_calls() == null) {
                continue;
            }
            toolChunks.add(delta.getTool_calls().get(0));
            message = ResponseHelper.combineMessage(message, delta);
        }

        assertEquals(Arrays.asList(0, 0, 1, 1, 0), indexes(toolChunks));
        assertEquals("call_1", toolChunks.get(0).getId());
        assertEquals("read_file", toolChunks.get(0).getFunction().getName());
        assertEquals("call_2", toolChunks.get(2).getId());
        assertEquals("read_file", toolChunks.get(2).getFunction().getName());
        assertNotNull(message);
        assertEquals(2, message.getTool_calls().size());
        assertEquals(0, message.getTool_calls().get(0).getIndex());
        assertEquals(1, message.getTool_calls().get(1).getIndex());
        assertEquals("{\"path\":\"a\"}", message.getTool_calls().get(0).getFunction().getArguments());
        assertEquals("{\"path\":\"b\"}", message.getTool_calls().get(1).getFunction().getArguments());
    }

    private static List<Integer> indexes(List<Message.ToolCall> toolChunks) {
        List<Integer> indexes = new ArrayList<>();
        for(Message.ToolCall toolChunk : toolChunks) {
            indexes.add(toolChunk.getIndex());
        }
        return indexes;
    }

    private static List<StreamMessageResponse> events() {
        List<StreamMessageResponse> events = new ArrayList<>();
        events.add(StreamMessageResponse.messageStart(MessageResponse.builder()
                .id("msg_test")
                .type("message")
                .role("assistant")
                .model(MODEL)
                .content(Collections.emptyList())
                .usage(MessageResponse.Usage.builder().inputTokens(7).build())
                .build()));
        events.add(StreamMessageResponse.contentBlockStart(1,
                new MessageResponse.ResponseToolUseBlock("call_1", "read_file", Collections.emptyMap())));
        events.add(StreamMessageResponse.contentBlockDelta(1, new StreamMessageResponse.InputJsonDelta("{\"path\":")));
        events.add(StreamMessageResponse.contentBlockStart(2,
                new MessageResponse.ResponseToolUseBlock("call_2", "read_file", Collections.emptyMap())));
        events.add(StreamMessageResponse.contentBlockDelta(2, new StreamMessageResponse.InputJsonDelta("{\"path\":\"b\"}")));
        events.add(StreamMessageResponse.contentBlockDelta(1, new StreamMessageResponse.InputJsonDelta("\"a\"}")));
        events.add(StreamMessageResponse.contentBlockStop(1));
        events.add(StreamMessageResponse.contentBlockStop(2));
        events.add(StreamMessageResponse.messageDelta(
                StreamMessageResponse.MessageDeltaInfo.builder().stopReason("tool_use").build(),
                StreamMessageResponse.StreamUsage.builder().outputTokens(10).build()));
        events.add(StreamMessageResponse.messageStop());
        return events;
    }

    private static class CapturingCallback implements Callbacks.StreamCompletionCallback {
        private final List<StreamCompletionResponse> responses = new ArrayList<>();

        @Override
        public void onOpen() {
        }

        @Override
        public void callback(StreamCompletionResponse msg) {
            responses.add(msg);
        }

        @Override
        public void done() {
        }

        @Override
        public void finish() {
        }

        @Override
        public void finish(BellaException exception) {
        }

        @Override
        public void send(Object data) {
        }
    }
}
