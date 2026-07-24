package com.ke.bella.openapi.protocol.message;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptor;
import com.ke.bella.openapi.protocol.completion.CompletionAdaptorDelegator;
import com.ke.bella.openapi.protocol.completion.CompletionLogHandler;
import com.ke.bella.openapi.protocol.completion.CompletionProperty;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.completion.CompletionResponse;
import com.ke.bella.openapi.protocol.completion.Message;
import com.ke.bella.openapi.protocol.completion.StreamCompletionResponse;
import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Put;

public class MessageToChatQueueAdaptorTest {
    private QueueClient queueClient;
    private EndpointProcessData processData;
    private MessageToChatQueueAdaptor<CompletionProperty> adaptor;

    @Before
    public void setUp() {
        queueClient = mock(QueueClient.class);
        MessageRequest request = messageRequest(false);
        EndpointContext.setEndpointData("/v1/messages", request.getModel(), request);
        processData = EndpointContext.getProcessData();
        processData.setApikey("ak-test");
        processData.setEncodingType("cl100k_base");
        processData.setMaxWaitSec(30);
        adaptor = new MessageToChatQueueAdaptor<>(new FakeMessageDelegatorAdaptor(), queueClient, processData);
    }

    @After
    public void tearDown() {
        EndpointContext.clearAll();
    }

    @Test
    public void createMessages_convertsMessageToChatCompletionAndPutsChatEndpointTask() {
        AtomicReference<Put> capturedPut = new AtomicReference<>();
        doAnswer(inv -> {
            capturedPut.set(inv.getArgument(0));
            return completionResponse();
        }).when(queueClient).blockingPut(any(Put.class), eq("ak-test"), eq(CompletionResponse.class), any());

        MessageResponse response = adaptor.createMessages(messageRequest(false), "url", new CompletionProperty());

        verify(queueClient).blockingPut(any(Put.class), eq("ak-test"), eq(CompletionResponse.class), any());
        Put put = capturedPut.get();
        assertNotNull(put);
        assertEquals("/v1/chat/completions", put.getEndpoint());
        assertEquals(Integer.valueOf(30), put.getTimeout());
        assertEquals("/v1/messages", processData.getEndpoint());

        Map<String, Object> data = put.getData();
        assertEquals("claude-3", data.get("model"));
        assertEquals(false, data.get("stream"));
        assertTrue(data.get("messages") instanceof List);

        assertEquals("msg-test", response.getId());
        assertEquals("claude-3", response.getModel());
        assertEquals("end_turn", response.getStopReason());
        assertEquals("pong", ((MessageResponse.ResponseTextBlock) response.getContent().get(0)).getText());
    }

    @Test
    public void createMessages_withoutDownstreamUsage_fallbackCalculatesUsageFromChatRequestAndResponse() {
        doAnswer(inv -> completionResponseWithoutUsage())
                .when(queueClient).blockingPut(any(Put.class), eq("ak-test"), eq(CompletionResponse.class), any());

        adaptor.createMessages(messageRequest(false), "url", new CompletionProperty());

        new CompletionLogHandler().process(processData);

        CompletionResponse.TokenUsage usage = (CompletionResponse.TokenUsage) processData.getUsage();
        assertNotNull(usage);
        assertTrue(usage.getPrompt_tokens() > 0);
        assertTrue(usage.getCompletion_tokens() > 0);
        assertEquals(usage.getPrompt_tokens() + usage.getCompletion_tokens(), usage.getTotal_tokens());
    }

    @Test
    public void streamMessages_convertsMessageToChatCompletionAndPutsChatEndpointTask() {
        AtomicReference<Put> capturedPut = new AtomicReference<>();
        doAnswer(inv -> {
            capturedPut.set(inv.getArgument(0));
            return null;
        }).when(queueClient).streamingPut(any(Put.class), eq("ak-test"), any(BellaEventSourceListener.class));

        adaptor.streamMessages(messageRequest(true), "url", new CompletionProperty(), new NoopStreamCallback());

        verify(queueClient).streamingPut(any(Put.class), eq("ak-test"), any(BellaEventSourceListener.class));
        Put put = capturedPut.get();
        assertNotNull(put);
        assertEquals("/v1/chat/completions", put.getEndpoint());
        assertEquals("/v1/messages", processData.getEndpoint());

        Map<String, Object> data = put.getData();
        assertEquals("claude-3", data.get("model"));
        assertEquals(true, data.get("stream"));
        assertTrue(data.get("messages") instanceof List);
    }

    private MessageRequest messageRequest(boolean stream) {
        return MessageRequest.builder()
                .model("claude-3")
                .system("You are concise.")
                .messages(Arrays.asList(MessageRequest.InputMessage.builder()
                        .role("user")
                        .content("ping")
                        .build()))
                .maxTokens(100)
                .stream(stream)
                .build();
    }

    private CompletionResponse completionResponse() {
        return CompletionResponse.builder()
                .id("msg-test")
                .choices(Arrays.asList(CompletionResponse.Choice.builder()
                        .finish_reason("stop")
                        .message(Message.builder()
                                .role("assistant")
                                .content("pong")
                                .build())
                        .build()))
                .usage(CompletionResponse.TokenUsage.builder()
                        .prompt_tokens(3)
                        .completion_tokens(4)
                        .total_tokens(7)
                        .build())
                .build();
    }

    private CompletionResponse completionResponseWithoutUsage() {
        return CompletionResponse.builder()
                .id("msg-test")
                .choices(Arrays.asList(CompletionResponse.Choice.builder()
                        .finish_reason("stop")
                        .message(Message.builder()
                                .role("assistant")
                                .content("pong")
                                .build())
                        .build()))
                .build();
    }

    private static class FakeMessageDelegatorAdaptor implements MessageDelegatorAdaptor<CompletionProperty> {
        private final FakeCompletionAdaptor completionAdaptor = new FakeCompletionAdaptor();

        @Override
        public CompletionAdaptor<CompletionProperty> delegator() {
            return completionAdaptor;
        }

        @Override
        public AnthropicAdaptor anthropicAdaptor() {
            return null;
        }

        @Override
        public boolean isNativeSupport() {
            return false;
        }

        @Override
        public String getDescription() {
            return "fake";
        }
    }

    private static class FakeCompletionAdaptor implements CompletionAdaptorDelegator<CompletionProperty> {
        @Override
        public CompletionResponse completion(CompletionRequest request, String url, CompletionProperty property,
                Callbacks.HttpDelegator delegator) {
            return delegator.request(request, CompletionResponse.class, null);
        }

        @Override
        public void streamCompletion(CompletionRequest request, String url, CompletionProperty property,
                Callbacks.StreamCompletionCallback callback, Callbacks.StreamDelegator delegator) {
            delegator.request(request, mock(BellaEventSourceListener.class));
        }

        @Override
        public CompletionResponse completion(CompletionRequest request, String url, CompletionProperty property) {
            return CompletionResponse.builder().build();
        }

        @Override
        public void streamCompletion(CompletionRequest request, String url, CompletionProperty property,
                Callbacks.StreamCompletionCallback callback) {
        }

        @Override
        public String getDescription() {
            return "fake";
        }

        @Override
        public Class<?> getPropertyClass() {
            return CompletionProperty.class;
        }
    }

    private static class NoopStreamCallback implements Callbacks.StreamCompletionCallback {
        @Override
        public void onOpen() {
        }

        @Override
        public void callback(StreamCompletionResponse msg) {
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
