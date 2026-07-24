package com.ke.bella.openapi.protocol.completion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.protocol.message.MessageResponse;
import com.ke.bella.openapi.protocol.message.StreamMessageResponse;
import com.ke.bella.openapi.protocol.message.TransferToCompletionsUtils;
import com.ke.bella.openapi.worker.WorkerMessageStreamingCallback;
import com.ke.bella.openapi.worker.WorkerStreamingCallback;
import com.ke.bella.queue.QueueClient;
import com.ke.bella.queue.TaskWrapper;
import com.theokanning.openai.queue.Task;

@RunWith(MockitoJUnitRunner.class)
public class QueueAdaptorChannelCodeTest {

    @Mock
    private CompletionAdaptorDelegator<CompletionProperty> delegator;
    @Mock
    private QueueClient queueClient;

    private EndpointProcessData processData;
    private QueueAdaptor<CompletionProperty> adaptor;

    @Before
    public void setUp() {
        processData = new EndpointProcessData();
        processData.setChannelCode("original-channel");
        processData.setApikey("ak-test");
        processData.setEndpoint("/v1/chat/completions");
        processData.setMaxWaitSec(30);
        processData.setQueueName("queue-a");
        adaptor = new QueueAdaptor<>(delegator, queueClient, processData);
    }

    @Test
    public void blockingResponseAndErrorCallback_keepOriginalProcessDataChannel() {
        CompletionResponse response = new CompletionResponse();
        response.setChannelCode("worker-channel");
        AtomicReference<String> callbackChannel = new AtomicReference<>();
        Callbacks.ChannelErrorCallback<CompletionResponse> originalCallback =
                (channelResponse, httpResponse) -> callbackChannel.set(processData.getChannelCode());

        doAnswer(inv -> {
            Callbacks.HttpDelegator httpDelegator = inv.getArgument(3);
            return httpDelegator.request(inv.getArgument(0), CompletionResponse.class, originalCallback);
        }).when(delegator).completion(any(), any(), any(), any());
        doAnswer(inv -> {
            com.theokanning.openai.queue.Put put = inv.getArgument(0);
            assertEquals("queue-a", put.getQueue());
            Callbacks.ChannelErrorCallback<CompletionResponse> callback = inv.getArgument(3);
            assertSame(originalCallback, callback);
            callback.callback(response, null);
            return response;
        }).when(queueClient).blockingPut(any(), eq("ak-test"), eq(CompletionResponse.class), any());

        CompletionResponse actual = adaptor.completion(mock(CompletionRequest.class), "url", mock(CompletionProperty.class));

        assertSame(response, actual);
        assertEquals("original-channel", callbackChannel.get());
        assertEquals("original-channel", processData.getChannelCode());
    }

    @Test
    public void streamingCallback_isPassedThroughWithoutChannelAwareWrapper() {
        Callbacks.StreamCompletionCallback sink = mock(Callbacks.StreamCompletionCallback.class);
        StreamCompletionResponse msg = new StreamCompletionResponse();
        msg.setChannelCode("worker-channel");

        doAnswer(inv -> {
            Callbacks.StreamCompletionCallback callback = inv.getArgument(3);
            assertSame(sink, callback);
            callback.callback(msg);
            Callbacks.StreamDelegator streamDelegator = inv.getArgument(4);
            streamDelegator.request(inv.getArgument(0), mock(BellaEventSourceListener.class));
            return null;
        }).when(delegator).streamCompletion(any(), any(), any(), any(), any());
        doAnswer(inv -> null).when(queueClient).streamingPut(any(), eq("ak-test"), any(BellaEventSourceListener.class));

        adaptor.streamCompletion(mock(CompletionRequest.class), "url", mock(CompletionProperty.class), sink);

        verify(sink).callback(msg);
        verify(queueClient).streamingPut(any(), eq("ak-test"), any(BellaEventSourceListener.class));
        assertEquals("original-channel", processData.getChannelCode());
    }

    @Test
    public void messageErrorProgress_convertToCompletionPreservesChannelCode() {
        StreamMessageResponse msg = StreamMessageResponse.error("channel_error", "failed");
        msg.setChannelCode("new-channel");

        StreamCompletionResponse converted = TransferToCompletionsUtils.convertStreamResponse(
                msg, "model", "id", new AtomicInteger(0), null);

        assertEquals("new-channel", converted.getChannelCode());
        assertEquals("failed", converted.getError().getMessage());
    }

    @Test
    public void messageProgress_convertToCompletionPreservesChannelCode() {
        Map<String, Object> delta = new HashMap<>();
        delta.put("type", "text_delta");
        delta.put("text", "hello");
        StreamMessageResponse msg = StreamMessageResponse.builder()
                .type("content_block_delta")
                .index(0)
                .delta(delta)
                .build();
        msg.setChannelCode("new-channel");

        StreamCompletionResponse converted = TransferToCompletionsUtils.convertStreamResponse(
                msg, "model", "id", new AtomicInteger(0), null);

        assertEquals("new-channel", converted.getChannelCode());
        assertEquals("hello", converted.getChoices().get(0).getDelta().getContent());
    }

    @Test
    public void workerStreamingCallback_callback_injectsChannelCodeIntoResponse() {
        processData.setChannelCode("ch-worker-001");
        AtomicReference<Object> captured = new AtomicReference<>();

        TaskWrapper taskWrapper = mock(TaskWrapper.class);
        doAnswer(inv -> {
            captured.set(inv.getArgument(2));
            return null;
        }).when(taskWrapper).emitProgress(any(), any(), any());

        WorkerStreamingCallback cb = new WorkerStreamingCallback(taskWrapper, processData, null, null, () -> {});
        cb.callback(new StreamCompletionResponse());

        StreamCompletionResponse sent = (StreamCompletionResponse) captured.get();
        assertEquals("ch-worker-001", sent.getChannelCode());
    }

    @Test
    public void workerStreamingCallback_send_stringDataPassedThrough() {
        processData.setChannelCode("ch-worker-001");
        AtomicReference<Object> captured = new AtomicReference<>();

        TaskWrapper taskWrapper = mock(TaskWrapper.class);
        doAnswer(inv -> {
            captured.set(inv.getArgument(2));
            return null;
        }).when(taskWrapper).emitProgress(any(), any(), any());

        WorkerStreamingCallback cb = new WorkerStreamingCallback(taskWrapper, processData, null, null, () -> {});
        cb.send("[DONE]");

        assertEquals("[DONE]", captured.get());
    }

    @Test
    public void workerMessageStreamingCallback_send_injectsTopLevelChannelCodeOnly() {
        processData.setChannelCode("ch-message-001");
        AtomicReference<String> capturedEvent = new AtomicReference<>();
        AtomicReference<Object> captured = new AtomicReference<>();

        TaskWrapper taskWrapper = mock(TaskWrapper.class);
        doAnswer(inv -> {
            capturedEvent.set(inv.getArgument(1));
            captured.set(inv.getArgument(2));
            return null;
        }).when(taskWrapper).emitProgress(any(), any(), any());

        WorkerMessageStreamingCallback cb = new WorkerMessageStreamingCallback(taskWrapper, processData, null, null, () -> {});
        cb.send(StreamMessageResponse.messageStart(new MessageResponse()));

        StreamMessageResponse sent = (StreamMessageResponse) captured.get();
        assertEquals("message_start", capturedEvent.get());
        assertEquals("ch-message-001", sent.getChannelCode());
        assertNull(sent.getMessage().getChannelCode());
    }

    @Test
    public void workerStreamingCallback_finishError_emitsChannelAwareProgressBeforeComplete() {
        processData.setChannelCode("ch-worker-001");
        TaskWrapper taskWrapper = mockTaskWrapper("task-1");
        AtomicReference<Object> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(inv.getArgument(2));
            return null;
        }).when(taskWrapper).emitProgress(any(), any(), any());

        WorkerStreamingCallback cb = new WorkerStreamingCallback(taskWrapper, processData, null, null, () -> {});
        cb.finish(new BellaException.ChannelException(502, "Bad Gateway"));

        InOrder inOrder = inOrder(taskWrapper);
        inOrder.verify(taskWrapper).emitProgress(any(), eq("message"), any());
        inOrder.verify(taskWrapper).markComplete(any());

        StreamCompletionResponse sent = (StreamCompletionResponse) captured.get();
        assertEquals("ch-worker-001", sent.getChannelCode());
        assertEquals("Bad Gateway", sent.getError().getMessage());
    }

    @Test
    public void workerStreamingCallback_finishError_progressFailureStillCompletesTask() {
        TaskWrapper taskWrapper = mockTaskWrapper("task-1");
        doThrow(new RuntimeException("progress failed")).when(taskWrapper).emitProgress(any(), any(), any());

        WorkerStreamingCallback cb = new WorkerStreamingCallback(taskWrapper, processData, null, null, () -> {});
        cb.finish(new BellaException.ChannelException(502, "Bad Gateway"));

        verify(taskWrapper).markComplete(any());
    }

    @Test
    public void workerMessageStreamingCallback_finishError_emitsMessageErrorProgressWithChannelCode() {
        processData.setChannelCode("ch-message-001");
        TaskWrapper taskWrapper = mockTaskWrapper("task-1");
        AtomicReference<Object> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(inv.getArgument(2));
            return null;
        }).when(taskWrapper).emitProgress(any(), any(), any());

        WorkerMessageStreamingCallback cb = new WorkerMessageStreamingCallback(taskWrapper, processData, null, null, () -> {});
        cb.finish(new BellaException.ChannelException(502, "Bad Gateway"));

        InOrder inOrder = inOrder(taskWrapper);
        inOrder.verify(taskWrapper).emitProgress(any(), eq("message"), any());
        inOrder.verify(taskWrapper).markComplete(any());

        StreamMessageResponse sent = (StreamMessageResponse) captured.get();
        assertEquals("error", sent.getType());
        assertEquals("ch-message-001", sent.getChannelCode());
        assertEquals("Bad Gateway", sent.getError().getMessage());
    }

    @Test
    public void openapiResponse_channelCodeFieldExists() {
        OpenapiResponse response = new OpenapiResponse();
        assertNull(response.getChannelCode());
        response.setChannelCode("ch-task-001");
        assertEquals("ch-task-001", response.getChannelCode());
    }

    private TaskWrapper mockTaskWrapper(String taskId) {
        Task task = new Task();
        task.setTaskId(taskId);
        TaskWrapper taskWrapper = mock(TaskWrapper.class);
        org.mockito.Mockito.when(taskWrapper.getTask()).thenReturn(task);
        return taskWrapper;
    }
}
