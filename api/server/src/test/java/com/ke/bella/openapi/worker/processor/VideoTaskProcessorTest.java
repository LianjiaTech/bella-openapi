package com.ke.bella.openapi.worker.processor;

import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.video.ChannelVideoResult;
import com.ke.bella.openapi.protocol.video.VideoAdaptor;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoProperty;
import com.ke.bella.openapi.protocol.video.QwenProperty;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.service.OpenAiService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VideoTaskProcessorTest {

    @Test
    @SuppressWarnings("unchecked")
    public void pollTaskResultIncludesChannelVideoIdForSuccessfulAndFailedResults() {
        VideoTaskProcessor.PollTask pollTask = new VideoTaskProcessor.PollTask(
                mock(OpenAiService.class), mock(AdaptorManager.class));

        Map<String, Object> completedResult = ReflectionTestUtils.invokeMethod(pollTask, "createResult",
                ChannelVideoResult.builder()
                        .channelVideoId("completed-vendor-id")
                        .status(VideoJob.Status.completed.name())
                        .build());
        Map<String, Object> failedResult = ReflectionTestUtils.invokeMethod(pollTask, "createResult",
                ChannelVideoResult.builder()
                        .channelVideoId("failed-vendor-id")
                        .status(VideoJob.Status.failed.name())
                        .build());

        assertThat(((Map<String, Object>) completedResult.get("body")).get("channelVideoId"))
                .isEqualTo("completed-vendor-id");
        assertThat(((Map<String, Object>) failedResult.get("body")).get("channelVideoId"))
                .isEqualTo("failed-vendor-id");
    }

    @Test
    public void putPollTaskUsesOriginalTaskIdAsTraceId() {
        OpenAiService openAiService = mock(OpenAiService.class);
        VideoTaskProcessor processor = VideoTaskProcessor.builder()
                .openAiService(openAiService)
                .adaptorManager(mock(AdaptorManager.class))
                .build();
        ChannelDB channel = new ChannelDB();
        channel.setChannelCode("video-channel");
        channel.setProtocol("test-protocol");
        channel.setUrl("https://channel");
        channel.setChannelInfo("{}");

        ReflectionTestUtils.invokeMethod(processor, "putPollTask", "original-task", "vendor-id", channel);

        ArgumentCaptor<Put> putCaptor = ArgumentCaptor.forClass(Put.class);
        verify(openAiService).putTask(putCaptor.capture());
        Put pollPut = putCaptor.getValue();
        assertThat(pollPut.getTraceId()).isEqualTo("original-task");
        assertThat(pollPut.getData().get("channelCode")).isEqualTo("video-channel");
        assertThat(pollPut.getData().get("channelVideoId")).isEqualTo("vendor-id");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pollTaskCompletesBothTasksAfterTerminalFailure() {
        OpenAiService openAiService = mock(OpenAiService.class);
        AdaptorManager adaptorManager = mock(AdaptorManager.class);
        VideoAdaptor<VideoProperty> adaptor = mock(VideoAdaptor.class);
        doReturn(QwenProperty.class).when(adaptor).getPropertyClass();
        when(adaptor.queryVideoTask(eq("vendor-id"), eq("https://channel"), any(VideoProperty.class)))
                .thenReturn(ChannelVideoResult.builder()
                        .channelVideoId("vendor-id")
                        .status(VideoJob.Status.failed.name())
                        .build());
        when(adaptorManager.getProtocolAdaptor(VideoJob.ENDPOINT, "test-protocol")).thenReturn(adaptor);

        VideoTaskProcessor.PollTask pollTask = new VideoTaskProcessor.PollTask(
                openAiService, adaptorManager);
        Task pollQueueTask = pollQueueTask();

        ReflectionTestUtils.invokeMethod(pollTask, "processTask", pollQueueTask);

        verify(openAiService).completeTask(eq("original-task"), anyMap());
        verify(openAiService).completeTask(eq("poll-task"), anyMap());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pollTaskCompletesPollTaskWhenOriginalTaskCompletionFails() {
        OpenAiService openAiService = mock(OpenAiService.class);
        AdaptorManager adaptorManager = mock(AdaptorManager.class);
        VideoAdaptor<VideoProperty> adaptor = mock(VideoAdaptor.class);
        doReturn(QwenProperty.class).when(adaptor).getPropertyClass();
        when(adaptor.queryVideoTask(eq("vendor-id"), eq("https://channel"), any(VideoProperty.class)))
                .thenReturn(ChannelVideoResult.builder()
                        .channelVideoId("vendor-id")
                        .status(VideoJob.Status.failed.name())
                        .build());
        when(adaptorManager.getProtocolAdaptor(VideoJob.ENDPOINT, "test-protocol")).thenReturn(adaptor);
        doThrow(new RuntimeException("queue unavailable"))
                .when(openAiService).completeTask(eq("original-task"), anyMap());

        VideoTaskProcessor.PollTask pollTask = new VideoTaskProcessor.PollTask(
                openAiService, adaptorManager);

        ReflectionTestUtils.invokeMethod(pollTask, "processTask", pollQueueTask());

        verify(openAiService).completeTask(eq("poll-task"), anyMap());
    }

    private Task pollQueueTask() {
        Task task = mock(Task.class);
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", "original-task");
        data.put("channelVideoId", "vendor-id");
        data.put("protocol", "test-protocol");
        data.put("channelUrl", "https://channel");
        data.put("channelInfo", "{}");
        data.put("createdAt", System.currentTimeMillis());
        when(task.getData()).thenReturn(data);
        when(task.getTaskId()).thenReturn("poll-task");
        return task;
    }
}
