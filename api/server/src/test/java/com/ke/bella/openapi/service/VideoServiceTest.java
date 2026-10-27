package com.ke.bella.openapi.service;

import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Task;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class VideoServiceTest {

    @InjectMocks
    private VideoService videoService;

    @Mock
    private QueueClient queueClient;

    @Mock
    private VideoTaskMetadataStore videoTaskMetadataStore;

    @Test
    public void queryVideoJobPrefersChannelVideoIdFromTaskResult() {
        Task task = taskWithResult("task-1", "running", "vendor-id-from-task");
        when(queueClient.getTaskDetail("task-1", "api-key")).thenReturn(task);

        assertThat(videoService.queryVideoJob("task-1", "api-key").getChannelVideoId())
                .isEqualTo("vendor-id-from-task");

        verify(videoTaskMetadataStore, never()).getChannelVideoId("task-1");
    }

    @Test
    public void queryVideoJobFallsBackToRedisWhenTaskResultHasNoChannelVideoId() {
        Task task = taskWithResult("task-1", "running", null);
        when(queueClient.getTaskDetail("task-1", "api-key")).thenReturn(task);
        when(videoTaskMetadataStore.getChannelVideoId("task-1")).thenReturn("vendor-id-from-redis");

        assertThat(videoService.queryVideoJob("task-1", "api-key").getChannelVideoId())
                .isEqualTo("vendor-id-from-redis");

        verify(videoTaskMetadataStore).getChannelVideoId("task-1");
    }

    private Task taskWithResult(String taskId, String status, String channelVideoId) {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getTaskId()).thenReturn(taskId);
        when(task.getStatus()).thenReturn(status);
        when(task.getData()).thenReturn(Collections.emptyMap());

        Map<String, Object> body = new HashMap<>();
        if(channelVideoId != null) {
            body.put("channelVideoId", channelVideoId);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("body", body);
        when(task.getResult()).thenReturn(result);
        return task;
    }
}
