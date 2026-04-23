package com.ke.bella.openapi.protocol.completion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.worker.WorkerStreamingCallback;
import com.ke.bella.queue.QueueClient;
import com.ke.bella.queue.TaskWrapper;

@RunWith(MockitoJUnitRunner.class)
public class QueueAdaptorChannelCodeTest {

    @Mock
    private CompletionAdaptorDelegator<CompletionProperty> delegator;
    @Mock
    private QueueClient queueClient;
    @Mock
    private Function<String, ChannelDB> channelLookup;

    private EndpointProcessData processData;
    private QueueAdaptor<CompletionProperty> adaptor;

    @Before
    public void setUp() {
        processData = new EndpointProcessData();
        processData.setChannelCode("original-channel");
        adaptor = new QueueAdaptor<>(delegator, queueClient, processData, channelLookup);
    }

    // ---- updateChannelInfo: channel 变化时全字段更新 ----

    @Test
    public void updateChannelInfo_workerReturnsDifferentChannel_updatesProcessData() {
        ChannelDB newChannel = buildChannel("new-channel", "http://new-url", "openai", "private", "supplier-x", "{}");
        when(channelLookup.apply("new-channel")).thenReturn(newChannel);

        StreamCompletionResponse msg = new StreamCompletionResponse();
        msg.setChannelCode("new-channel");
        invokeQueueStreamListenerDoCallback(msg, 1);

        assertEquals("new-channel", processData.getChannelCode());
        assertEquals("http://new-url", processData.getForwardUrl());
        assertEquals("openai", processData.getProtocol());
        assertEquals("{}", processData.getPriceInfo());
        assertEquals("supplier-x", processData.getSupplier());
        assertEquals(true, processData.isPrivate());
    }

    // ---- updateChannelInfo: channel 相同时跳过 DB 查询 ----

    @Test
    public void updateChannelInfo_workerReturnsSameChannel_skipsDbQuery() {
        StreamCompletionResponse msg = new StreamCompletionResponse();
        msg.setChannelCode("original-channel");
        invokeQueueStreamListenerDoCallback(msg, 1);

        verify(channelLookup, never()).apply(any());
        assertEquals("original-channel", processData.getChannelCode());
    }

    // ---- updateChannelInfo: channelCode 为空时直接设为 DEFAULT_CHANNEL，不查 DB ----

    @Test
    public void updateChannelInfo_workerReturnsBlankChannelCode_usesDefaultChannelWithoutDbQuery() {
        StreamCompletionResponse msg = new StreamCompletionResponse();
        msg.setChannelCode(null);
        invokeQueueStreamListenerDoCallback(msg, 1);

        verify(channelLookup, never()).apply(any());
        assertEquals("100000000-SELF-DEPLOYED", processData.getChannelCode());
    }

    // ---- updateChannelInfo: DB 查不到时 processData 不变 ----

    @Test
    public void updateChannelInfo_channelNotFoundInDb_processDataUnchanged() {
        when(channelLookup.apply("unknown-channel")).thenReturn(null);

        StreamCompletionResponse msg = new StreamCompletionResponse();
        msg.setChannelCode("unknown-channel");
        invokeQueueStreamListenerDoCallback(msg, 1);

        assertEquals("original-channel", processData.getChannelCode());
    }

    // ---- QueueStreamListener: 多条 chunk 只触发一次 DB 查询 ----

    @Test
    public void streamListener_onlyFirstChunkTriggersDbQuery() {
        ChannelDB newChannel = buildChannel("new-channel", "http://new-url", "openai", "public", "s", "{}");
        when(channelLookup.apply("new-channel")).thenReturn(newChannel);

        StreamCompletionResponse msg = new StreamCompletionResponse();
        msg.setChannelCode("new-channel");
        invokeQueueStreamListenerDoCallback(msg, 5);

        verify(channelLookup, times(1)).apply("new-channel");
    }

    // ---- WorkerStreamingCallback.callback: 注入 channelCode 到 StreamCompletionResponse ----

    @Test
    public void workerStreamingCallback_callback_injectsChannelCodeIntoResponse() {
        processData.setChannelCode("ch-worker-001");
        AtomicReference<Object> captured = new AtomicReference<>();

        TaskWrapper taskWrapper = mock(TaskWrapper.class);
        doAnswer(inv -> { captured.set(inv.getArgument(2)); return null; })
                .when(taskWrapper).emitProgress(any(), any(), any());

        WorkerStreamingCallback cb = new WorkerStreamingCallback(taskWrapper, processData, null, null, () -> {});
        cb.callback(new StreamCompletionResponse());

        StreamCompletionResponse sent = (StreamCompletionResponse) captured.get();
        assertEquals("ch-worker-001", sent.getChannelCode());
    }

    // ---- WorkerStreamingCallback.send: String 直接透传不注入 ----

    @Test
    public void workerStreamingCallback_send_stringDataPassedThrough() {
        processData.setChannelCode("ch-worker-001");
        AtomicReference<Object> captured = new AtomicReference<>();

        TaskWrapper taskWrapper = mock(TaskWrapper.class);
        doAnswer(inv -> { captured.set(inv.getArgument(2)); return null; })
                .when(taskWrapper).emitProgress(any(), any(), any());

        WorkerStreamingCallback cb = new WorkerStreamingCallback(taskWrapper, processData, null, null, () -> {});
        cb.send("[DONE]");

        assertEquals("[DONE]", captured.get());
    }

    // ---- TaskProcessor: OpenapiResponse 上有 channelCode 字段 ----

    @Test
    public void openapiResponse_channelCodeFieldExists() {
        OpenapiResponse response = new OpenapiResponse();
        assertNull(response.getChannelCode());
        response.setChannelCode("ch-task-001");
        assertEquals("ch-task-001", response.getChannelCode());
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private void invokeQueueStreamListenerDoCallback(StreamCompletionResponse msg, int times) {
        Callbacks.StreamCompletionCallback sink = mock(Callbacks.StreamCompletionCallback.class);
        doAnswer(inv -> {
            Callbacks.StreamCompletionCallback cb = inv.getArgument(3);
            for(int i = 0; i < times; i++) {
                cb.callback(msg);
            }
            return null;
        }).when(delegator).streamCompletion(any(), any(), any(), any(), any());

        adaptor.streamCompletion(mock(CompletionRequest.class), "url", mock(CompletionProperty.class), sink);
    }

    private ChannelDB buildChannel(String code, String url, String protocol,
            String visibility, String supplier, String priceInfo) {
        ChannelDB ch = new ChannelDB();
        ch.setChannelCode(code);
        ch.setUrl(url);
        ch.setProtocol(protocol);
        ch.setVisibility(visibility);
        ch.setSupplier(supplier);
        ch.setPriceInfo(priceInfo);
        return ch;
    }
}