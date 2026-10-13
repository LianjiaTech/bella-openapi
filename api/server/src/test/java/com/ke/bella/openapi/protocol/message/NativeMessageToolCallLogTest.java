package com.ke.bella.openapi.protocol.message;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.completion.AnthropicProperty;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.utils.JacksonUtils;
import org.junit.After;
import org.junit.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.PayloadPart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NativeMessageToolCallLogTest {
    private static final String MODEL = "GLM-5.3";

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void anthropicNativeStreamLogsSequentialToolArgumentsWithNames() {
        verifyAnthropicLog(false);
    }

    @Test
    public void anthropicNativeStreamLogsInterleavedToolArgumentsWithNames() {
        verifyAnthropicLog(true);
    }

    @Test
    public void awsNativeStreamLogsInterleavedToolArgumentsWithNames() {
        EndpointContext.setEndpointData("/v1/messages", MODEL, new MessageRequest());
        EndpointProcessData processData = EndpointContext.getProcessData();
        processData.setNativeSend(true);
        EndpointLogger logger = mock(EndpointLogger.class);
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback(processData, logger);
        AwsMessageAdaptor.AwsSseCompletionCallBack listener = new AwsMessageAdaptor.AwsSseCompletionCallBack(
                callback, true, MODEL, null, null, null, null);
        List<StreamMessageResponse> events = events(true);

        for(StreamMessageResponse event : events) {
            listener.visitChunk(PayloadPart.builder()
                    .bytes(SdkBytes.fromUtf8String(JacksonUtils.serialize(event)))
                    .build());
        }
        listener.run();

        assertLoggedTools(processData, logger);
        assertNativeToolEvents(events, callback.events);
    }

    private static void verifyAnthropicLog(boolean interleaved) {
        MessageRequest request = MessageRequest.builder().model(MODEL).stream(true).build();
        EndpointContext.setEndpointData("/v1/messages", MODEL, request);
        EndpointProcessData processData = EndpointContext.getProcessData();
        EndpointLogger logger = mock(EndpointLogger.class);
        CollectingStreamMessagesCallback callback = new CollectingStreamMessagesCallback(processData, logger);
        AnthropicProperty property = new AnthropicProperty();
        property.setDeployName(MODEL);
        property.setDefaultMaxToken(1000);
        List<StreamMessageResponse> events = events(interleaved);

        new AnthropicAdaptor().streamMessages(request, "unused", property, callback, (outbound, listener) -> {
            for(StreamMessageResponse event : events) {
                listener.onEvent(null, null, event.getType(), JacksonUtils.serialize(event));
            }
            listener.onClosed(null);
        });

        assertTrue(processData.isNativeSend());
        assertLoggedTools(processData, logger);
        assertNativeToolEvents(events, callback.events);
    }

    private static List<StreamMessageResponse> events(boolean interleaved) {
        List<StreamMessageResponse> events = new ArrayList<>();
        events.add(StreamMessageResponse.messageStart(MessageResponse.builder()
                .id("msg_test")
                .type("message")
                .role("assistant")
                .model(MODEL)
                .content(Collections.emptyList())
                .usage(MessageResponse.Usage.builder().inputTokens(7).build())
                .build()));
        events.add(StreamMessageResponse.contentBlockStart(0, new MessageResponse.ResponseThinkingBlock("", null)));
        events.add(StreamMessageResponse.contentBlockDelta(0, new StreamMessageResponse.ThinkingDelta("inspect files")));
        events.add(StreamMessageResponse.contentBlockStop(0));
        events.add(StreamMessageResponse.contentBlockStart(1,
                new MessageResponse.ResponseToolUseBlock("call_1", "read_file", Collections.emptyMap())));
        events.add(StreamMessageResponse.contentBlockDelta(1, new StreamMessageResponse.InputJsonDelta("{\"path\":")));
        if(interleaved) {
            events.add(StreamMessageResponse.contentBlockStart(2,
                    new MessageResponse.ResponseToolUseBlock("call_2", "read_file", Collections.emptyMap())));
            events.add(StreamMessageResponse.contentBlockDelta(2, new StreamMessageResponse.InputJsonDelta("{\"path\":\"b\"}")));
        }
        events.add(StreamMessageResponse.contentBlockDelta(1, new StreamMessageResponse.InputJsonDelta("\"a\"}")));
        events.add(StreamMessageResponse.contentBlockStop(1));
        if(!interleaved) {
            events.add(StreamMessageResponse.contentBlockStart(2,
                    new MessageResponse.ResponseToolUseBlock("call_2", "read_file", Collections.emptyMap())));
            events.add(StreamMessageResponse.contentBlockDelta(2, new StreamMessageResponse.InputJsonDelta("{\"path\":\"b\"}")));
        }
        events.add(StreamMessageResponse.contentBlockStop(2));
        events.add(StreamMessageResponse.messageDelta(
                StreamMessageResponse.MessageDeltaInfo.builder().stopReason("tool_use").build(),
                StreamMessageResponse.StreamUsage.builder().outputTokens(10).build()));
        events.add(StreamMessageResponse.messageStop());
        return events;
    }

    private static void assertLoggedTools(EndpointProcessData processData, EndpointLogger logger) {
        verify(logger).log(processData);
        CompletionResponse response = JacksonUtils.deserialize(JacksonUtils.serialize(processData.getResponse()), CompletionResponse.class);
        assertEquals("msg_test", response.getId());
        assertEquals(1, response.getChoices().size());
        CompletionResponse.Choice choice = response.getChoices().get(0);
        assertEquals(0, choice.getIndex());
        assertEquals("tool_calls", choice.getFinish_reason());
        assertEquals("inspect files", choice.getMessage().getReasoning_content());
        List<Message.ToolCall> tools = choice.getMessage().getTool_calls();
        assertEquals(2, tools.size());
        for(int toolIndex = 0; toolIndex < tools.size(); toolIndex++) {
            Message.ToolCall tool = tools.get(toolIndex);
            assertEquals(toolIndex, tool.getIndex());
            assertEquals("call_" + (toolIndex + 1), tool.getId());
            assertEquals("function", tool.getType());
            assertEquals("read_file", tool.getFunction().getName());
        }
        assertEquals("{\"path\":\"a\"}", tools.get(0).getFunction().getArguments());
        assertEquals("{\"path\":\"b\"}", tools.get(1).getFunction().getArguments());
        assertEquals(7, response.getUsage().getPrompt_tokens());
        assertEquals(10, response.getUsage().getCompletion_tokens());
    }

    private static void assertNativeToolEvents(List<StreamMessageResponse> expected, List<StreamMessageResponse> actual) {
        assertEquals(expected.size(), actual.size());
        for(int eventIndex = 0; eventIndex < expected.size(); eventIndex++) {
            StreamMessageResponse event = expected.get(eventIndex);
            assertEquals(event.getType(), actual.get(eventIndex).getType());
            if(event.getType().startsWith("content_block_")) {
                assertEquals(JacksonUtils.serialize(event), JacksonUtils.serialize(actual.get(eventIndex)));
            }
        }
    }

    private static class CollectingStreamMessagesCallback extends StreamMessagesCallback {
        private final List<StreamMessageResponse> events = new ArrayList<>();

        CollectingStreamMessagesCallback(EndpointProcessData processData, EndpointLogger logger) {
            super(null, processData, null, logger, null);
        }

        @Override
        public void send(Object data) {
            events.add((StreamMessageResponse) data);
        }
    }
}
