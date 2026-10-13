package com.ke.bella.openapi.worker;

import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.batch.BatchAdaptor;
import com.ke.bella.openapi.protocol.batch.BatchRetriableException;
import com.ke.bella.openapi.protocol.batch.OpenAiProperty;
import com.ke.bella.openapi.protocol.limiter.ConcurrentPermitLimiter;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.theokanning.openai.batch.Batch;
import com.theokanning.openai.batch.BatchRequest;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Queue;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.service.OpenAiService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BatchWorkerTest {

    private static final String PERMIT_KEY = "batch:rate:channel-a:batch_process";
    private static final int MAX_PERMITS = 1;
    private static final int PERMIT_TTL_SECONDS = 600;
    private static final String PERMIT_ID = "permit-1";

    @Mock
    private OpenAiService openAiService;
    @Mock
    private AdaptorManager adaptorManager;
    @Mock
    private ConcurrentPermitLimiter concurrentPermitLimiter;
    @Mock
    private Queue queue;
    @Mock
    private BatchAdaptor<OpenAiProperty> adaptor;

    private ChannelDB channel;

    @Before
    public void setUp() {
        channel = new ChannelDB();
        channel.setChannelCode("channel-a");
        channel.setSupplier("supplier-a");
        channel.setProtocol("OpenAiBatchAdaptor");
        channel.setQueueName("queue-a");
        channel.setUrl("http://batch.example/v1/batches");
        channel.setChannelInfo("{\"maxSize\":2}");

        when(adaptorManager.getProtocolAdaptor(eq("/v1/batches"), eq("OpenAiBatchAdaptor"), eq(BatchAdaptor.class))).thenReturn(adaptor);
        when(adaptor.getPropertyClass()).thenReturn((Class) OpenAiProperty.class);
    }

    @Test
    public void processTask_acquireFailed_doesNotTakeTasks() throws Exception {
        when(concurrentPermitLimiter.tryAcquire(PERMIT_KEY, MAX_PERMITS, PERMIT_TTL_SECONDS)).thenReturn(null);

        boolean result = invokeProcessTask(newTask());

        assertFalse(result);
        verify(openAiService, never()).takeTasks(any());
        verify(concurrentPermitLimiter, never()).tryRelease(any(), any());
    }

    @Test
    public void processTask_noTasks_releasesPermit() throws Exception {
        when(concurrentPermitLimiter.tryAcquire(PERMIT_KEY, MAX_PERMITS, PERMIT_TTL_SECONDS)).thenReturn(PERMIT_ID);
        when(openAiService.takeTasks(any())).thenReturn(Collections.emptyMap());

        boolean result = invokeProcessTask(newTask());

        assertFalse(result);
        verify(concurrentPermitLimiter).tryRelease(PERMIT_KEY, PERMIT_ID);
    }

    @Test
    public void processTask_uploadFailed_releasesPermit() throws Exception {
        Task task = mock(Task.class);
        when(concurrentPermitLimiter.tryAcquire(PERMIT_KEY, MAX_PERMITS, PERMIT_TTL_SECONDS)).thenReturn(PERMIT_ID);
        when(openAiService.takeTasks(any())).thenReturn(tasks(task));
        when(adaptor.uploadTasks(any(), any())).thenThrow(new RuntimeException("upload failed"));

        try {
            invokeProcessTask(newTask());
        } catch (RuntimeException ignored) {
        }

        verify(concurrentPermitLimiter).tryRelease(PERMIT_KEY, PERMIT_ID);
    }

    @Test
    public void processTask_retriesRetriableUploadFailure() throws Exception {
        Task task = mock(Task.class);
        Batch batch = mock(Batch.class);

        when(concurrentPermitLimiter.tryAcquire(PERMIT_KEY, MAX_PERMITS, PERMIT_TTL_SECONDS)).thenReturn(PERMIT_ID);
        when(openAiService.takeTasks(any())).thenReturn(tasks(task));
        when(adaptor.uploadTasks(any(), any()))
                .thenThrow(new BatchRetriableException("rate limit"))
                .thenThrow(new BatchRetriableException("server error"))
                .thenReturn("file-1");
        when(adaptor.createBatch(any(), eq(channel.getUrl()), any())).thenReturn(batch);
        when(batch.getId()).thenReturn("batch-1");
        when(batch.getStatus()).thenReturn("validating");
        when(queue.getEndpoint()).thenReturn("/v1/chat/completions");

        boolean result = invokeProcessTask(newTask());

        assertFalse(result);
        verify(adaptor, times(3)).uploadTasks(any(), any());
        ArgumentCaptor<BatchRequest> batchRequestCaptor = forClass(BatchRequest.class);
        ArgumentCaptor<Put> putCaptor = forClass(Put.class);
        verify(adaptor).createBatch(batchRequestCaptor.capture(), eq(channel.getUrl()), any());
        verify(openAiService).putTask(putCaptor.capture());
        assertEquals("7d", batchRequestCaptor.getValue().getCompletionWindow());
        assertEquals(Integer.valueOf(604800), putCaptor.getValue().getTimeout());
        verify(concurrentPermitLimiter).tryRelease(PERMIT_KEY, PERMIT_ID);
    }

    @Test
    public void processTask_returnsHasMoreWhenBatchIsFull() throws Exception {
        channel.setChannelInfo("{\"maxSize\":1}");
        Task task = mock(Task.class);
        Batch batch = mock(Batch.class);

        when(concurrentPermitLimiter.tryAcquire(PERMIT_KEY, MAX_PERMITS, PERMIT_TTL_SECONDS)).thenReturn(PERMIT_ID);
        when(openAiService.takeTasks(any())).thenReturn(tasks(task));
        when(adaptor.uploadTasks(any(), any())).thenReturn("file-1");
        when(adaptor.createBatch(any(), eq(channel.getUrl()), any())).thenReturn(batch);
        when(batch.getId()).thenReturn("batch-1");
        when(batch.getStatus()).thenReturn("validating");
        when(queue.getEndpoint()).thenReturn("/v1/chat/completions");

        boolean result = invokeProcessTask(newTask());

        assertTrue(result);
        verify(concurrentPermitLimiter).tryRelease(PERMIT_KEY, PERMIT_ID);
        verify(openAiService).putTask(any());
    }

    private BatchWorker.CreateBatchTask newTask() {
        return new BatchWorker.CreateBatchTask(openAiService, adaptorManager, channel, queue, concurrentPermitLimiter);
    }

    private boolean invokeProcessTask(BatchWorker.CreateBatchTask task) throws Exception {
        Method method = BatchWorker.CreateBatchTask.class.getDeclaredMethod("processTask");
        method.setAccessible(true);
        try {
            return (Boolean) method.invoke(task);
        } catch (InvocationTargetException e) {
            if(e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    private HashMap<String, List<Task>> tasks(Task task) {
        HashMap<String, List<Task>> result = new HashMap<>();
        result.put("queue-a:1", Collections.singletonList(task));
        return result;
    }
}
