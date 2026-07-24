package com.ke.bella.openapi.worker.processor;

import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.video.ChannelVideoResult;
import com.ke.bella.openapi.protocol.video.VideoAdaptor;
import com.ke.bella.openapi.protocol.video.VideoCreateRequest;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoProperty;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.TaskWrapper;
import com.theokanning.openai.file.File;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Take;
import com.theokanning.openai.queue.Task;
import com.theokanning.openai.service.OpenAiService;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Builder
@SuppressWarnings("all")
public class VideoTaskProcessor implements EndpointTaskProcessor {

    private static final long POLL_INTERVAL_MS = 10_000;
    private static final int POLL_PROCESS_TIMEOUT_SECONDS = 60;
    private static final long MAX_POLL_DURATION_MS = TimeUnit.HOURS.toMillis(24);
    private static final String CREATED_VENDOR_VIDEO_QUEUE = "created_vendor_video_queue";

    private final AdaptorManager adaptorManager;
    private final OpenAiService openAiService;
    private volatile PollTask pollTask;

    public void start() {
        pollTask = new PollTask(openAiService, adaptorManager);
        TaskExecutor.submit(pollTask);
    }

    public void stop() {
        if(pollTask != null) {
            pollTask.stop();
        }
    }

    public boolean isStopped() {
        return pollTask == null || pollTask.isStopped();
    }

    @Override
    public String endpoint() {
        return VideoJob.ENDPOINT;
    }

    @Override
    public TaskProcessResult execute(TaskWrapper taskWrapper, ChannelDB channel, TaskSlot slot) {
        String taskId = taskWrapper.getTask().getTaskId();
        log.info("Video task started, taskId: {}, channel: {}", taskId, channel.getChannelCode());
        try {
            VideoCreateRequest request = taskWrapper.getPayload(VideoCreateRequest.class);

            VideoAdaptor<VideoProperty> adaptor = getAdaptor(channel.getProtocol());
            VideoProperty property = getProperty(adaptor, channel.getChannelInfo());
            String channelVideoId = adaptor.submitVideoTask(request, channel.getUrl(), property, taskId);
            log.info("Video task submitted to channel, taskId: {}, channelVideoId: {}", taskId, channelVideoId);

            putPollTask(taskId, channelVideoId, channel);
            log.info("Video poll task created, taskId: {}, channelVideoId: {}", taskId, channelVideoId);
            return TaskProcessResult.SYNC_DONE;
        } catch (Exception e) {
            log.error("Video task execution failed, taskId: {}, channel: {}", taskId, channel.getChannelCode(), e);
            taskWrapper.markComplete(createErrorResult("execution_error", e.getMessage()));
            return TaskProcessResult.SYNC_DONE;
        }
    }

    private void putPollTask(String taskId, String channelVideoId, ChannelDB channel) {
        Map<String, Object> data = new HashMap<>();
        data.put("taskId", taskId);
        data.put("channelVideoId", channelVideoId);
        data.put("protocol", channel.getProtocol());
        data.put("channelUrl", channel.getUrl());
        data.put("channelInfo", channel.getChannelInfo());
        data.put("createdAt", System.currentTimeMillis());

        Put put = Put.builder()
                .queue(CREATED_VENDOR_VIDEO_QUEUE)
                .level(1)
                .endpoint(VideoJob.ENDPOINT)
                .data(data)
                .timeout(24 * 60 * 60)
                .build();
        openAiService.putTask(put);
    }

    @SuppressWarnings("unchecked")
    private VideoAdaptor<VideoProperty> getAdaptor(String protocol) {
        VideoAdaptor<?> adaptor = (VideoAdaptor<?>) adaptorManager.getProtocolAdaptor(
                VideoJob.ENDPOINT, protocol);
        if(adaptor == null) {
            throw new IllegalStateException("No video adaptor found for protocol: " + protocol);
        }
        return (VideoAdaptor<VideoProperty>) adaptor;
    }

    private VideoProperty getProperty(VideoAdaptor<VideoProperty> adaptor, String channelInfo) {
        return (VideoProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
    }

    private Map<String, Object> createErrorResult(String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", VideoJob.Status.failed.name());
        Map<String, String> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        body.put("error", error);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("status_code", 500);
        resultMap.put("body", body);
        return resultMap;
    }

    @Slf4j
    public static class PollTask implements Runnable {
        private final OpenAiService openAiService;
        private final AdaptorManager adaptorManager;

        @Getter
        private volatile boolean stopped = false;

        public PollTask(OpenAiService openAiService, AdaptorManager adaptorManager) {
            this.openAiService = openAiService;
            this.adaptorManager = adaptorManager;
        }

        @Override
        public void run() {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                try {
                    boolean hasWork;
                    do {
                        hasWork = poll();
                    } while (hasWork && !stopped);

                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Poll Video Status error", e);
                    try {
                        Thread.sleep(POLL_INTERVAL_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        private boolean poll() {
            Take take = Take.builder()
                    .queues(Collections.singletonList(CREATED_VENDOR_VIDEO_QUEUE + ":1"))
                    .size(10)
                    .processTimeout(POLL_PROCESS_TIMEOUT_SECONDS)
                    .processMaxRetries(-1)
                    .build();

            List<Task> tasks = openAiService.takeTasks(take)
                    .values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            if(tasks.isEmpty()) {
                return false;
            }

            for (Task task : tasks) {
                processTask(task);
            }

            return tasks.size() >= 10;
        }

        private void processTask(Task task) {
            Map<String, Object> data = task.getData();
            String taskId = MapUtils.getString(data, "taskId");
            String channelVideoId = MapUtils.getString(data, "channelVideoId");
            String protocol = MapUtils.getString(data, "protocol");
            String channelUrl = MapUtils.getString(data, "channelUrl");
            String channelInfo = MapUtils.getString(data, "channelInfo");
            long createdAt = MapUtils.getLongValue(data, "createdAt");

            try {
                ChannelVideoResult result = resolve(taskId, channelVideoId, protocol, channelUrl, channelInfo, createdAt);
                log.info("Video task poll result, taskId: {}, channelVideoId: {}, result: {}", taskId, channelVideoId,
                        JacksonUtils.serialize(result));
                if(!isTerminalState(result.getStatus())) {
                    return;
                }

                if(VideoJob.Status.completed.name().equals(result.getStatus())) {
                    VideoAdaptor<VideoProperty> adaptor = getAdaptor(protocol);
                    VideoProperty property = getProperty(adaptor, channelInfo);
                    transferVideoToFile(adaptor, result, channelUrl, property);
                }

                Map<String, Object> resultMap = createResult(result);
                completeTask(taskId, resultMap);
                completeTask(task.getTaskId(), resultMap);
                log.info("Video task completed, taskId: {}, status: {}", taskId, result.getStatus());
            } catch (Exception e) {
                log.error("Error processing video poll task: {}, originalTaskId: {}", task.getTaskId(), taskId, e);
                Map<String, Object> errorResult = createErrorResult("execution_error", e.getMessage());
                completeTask(taskId, errorResult);
                completeTask(task.getTaskId(), errorResult);
            }
        }

        private ChannelVideoResult resolve(String taskId, String channelVideoId, String protocol,
                String channelUrl, String channelInfo, long createdAt) {
            if(System.currentTimeMillis() - createdAt > MAX_POLL_DURATION_MS) {
                log.warn("Video task polling timeout, taskId: {}, channelVideoId: {}", taskId, channelVideoId);
                return ChannelVideoResult.builder()
                        .channelVideoId(channelVideoId)
                        .status(VideoJob.Status.failed.name())
                        .error(ChannelVideoResult.ErrorInfo.builder()
                                .code("poll_timeout")
                                .message("Polling exceeded max duration of " + (MAX_POLL_DURATION_MS / 1000) + "s")
                                .build())
                        .build();
            }

            VideoAdaptor<VideoProperty> adaptor = getAdaptor(protocol);
            VideoProperty property = getProperty(adaptor, channelInfo);
            return adaptor.queryVideoTask(channelVideoId, channelUrl, property);
        }

        @SuppressWarnings("unchecked")
        private VideoAdaptor<VideoProperty> getAdaptor(String protocol) {
            VideoAdaptor<?> adaptor = (VideoAdaptor<?>) adaptorManager.getProtocolAdaptor(
                    VideoJob.ENDPOINT, protocol);
            if(adaptor == null) {
                throw new IllegalStateException("No video adaptor found for protocol: " + protocol);
            }
            return (VideoAdaptor<VideoProperty>) adaptor;
        }

        private VideoProperty getProperty(VideoAdaptor<VideoProperty> adaptor, String channelInfo) {
            return (VideoProperty) JacksonUtils.deserialize(channelInfo, adaptor.getPropertyClass());
        }

        private void transferVideoToFile(VideoAdaptor<VideoProperty> adaptor, ChannelVideoResult result,
                String baseUrl, VideoProperty property) {
            File file = adaptor.transferVideoToFile(result.getChannelVideoId(), baseUrl, property, openAiService);
            if(file != null && file.getId() != null) {
                result.setFileId(file.getId());
            } else {
                log.warn("Transfer returned null or empty fileId for channelVideoId: {}", result.getChannelVideoId());
            }
        }

        private boolean isTerminalState(String status) {
            return VideoJob.Status.completed.name().equals(status) ||
                    VideoJob.Status.failed.name().equals(status) ||
                    VideoJob.Status.cancelled.name().equals(status);
        }

        private Map<String, Object> createResult(ChannelVideoResult result) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", result.getStatus());
            if(result.getFileId() != null) {
                body.put("boundFileId", result.getFileId());
            }
            if(result.getUsage() != null) {
                body.put("usage", result.getUsage());
            }
            if(result.getActualSeconds() != null) {
                body.put("actualSeconds", result.getActualSeconds());
            }
            if(result.getSize() != null) {
                body.put("size", result.getSize());
            }
            if(result.getError() != null) {
                body.put("error", result.getError());
            }

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("status_code", VideoJob.Status.completed.name().equals(result.getStatus()) ? 200 : 500);
            resultMap.put("body", body);
            return resultMap;
        }

        private Map<String, Object> createErrorResult(String code, String message) {
            Map<String, Object> body = new HashMap<>();
            body.put("status", VideoJob.Status.failed.name());
            Map<String, String> error = new HashMap<>();
            error.put("code", code);
            error.put("message", message);
            body.put("error", error);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("status_code", 500);
            resultMap.put("body", body);
            return resultMap;
        }

        private void completeTask(String taskId, Map<String, Object> result) {
            try {
                openAiService.completeTask(taskId, result);
            } catch (Exception e) {
                log.error("Failed to complete task, taskId: {}", taskId, e);
            }
        }

        public void stop() {
            stopped = true;
        }
    }
}
