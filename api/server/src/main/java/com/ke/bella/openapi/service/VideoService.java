package com.ke.bella.openapi.service;

import com.ke.bella.openapi.protocol.OpenapiResponse.OpenapiError;
import com.ke.bella.openapi.protocol.video.VideoCreateRequest;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoUsage;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.queue.QueueClient;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

@Component
@Slf4j
public class VideoService {

    @Resource
    private QueueClient queueClient;

    public VideoJob createVideoJob(VideoCreateRequest request, String apikey) {
        String model = request.getModel();

        Put put = Put.builder()
                .data(JacksonUtils.toMap(request))
                .level(1)
                .endpoint(VideoJob.ENDPOINT)
                .build();

        Task task = queueClient.put(put, apikey);
        String taskId = task.getTaskId();

        log.info("[VideoJob] Created via Bella Queue: taskId={}, model={}",
                taskId, model);

        VideoJob videoJob = new VideoJob();
        videoJob.setId(taskId);
        videoJob.setModel(model);
        videoJob.setStatus(VideoJob.Status.queued.name());
        videoJob.setPrompt(request.getPrompt());
        videoJob.setSeconds(request.getSeconds());
        videoJob.setSize(request.getSize());
        return videoJob;
    }

    public VideoJob queryVideoJob(String taskId, String apikey) {
        Task task = queueClient.getTaskDetail(taskId, apikey);
        if(task == null) {
            return null;
        }
        return mapTaskToVideoJob(task);
    }

    public VideoJob cancelVideoJob(String taskId, String apikey) {
        Task task = queueClient.cancelTask(taskId, apikey);
        if(task == null) {
            return null;
        }
        return mapTaskToVideoJob(task);
    }

    @SuppressWarnings("unchecked")
    private VideoJob mapTaskToVideoJob(Task task) {
        VideoJob videoJob = new VideoJob();
        videoJob.setId(task.getTaskId());

        String status = task.getStatus();
        if("running".equals(status)) {
            status = VideoJob.Status.processing.name();
        } else if("pending".equals(status)) {
            status = VideoJob.Status.queued.name();
        }
        videoJob.setStatus(status);

        Map<String, Object> data = task.getData();
        if(data != null) {
            videoJob.setModel((String) data.get("model"));
            videoJob.setPrompt((String) data.get("prompt"));
            if(data.get("seconds") != null) {
                videoJob.setSeconds(String.valueOf(data.get("seconds")));
            }
            if(data.get("size") != null) {
                videoJob.setSize((String) data.get("size"));
            }
        }

        Object result = task.getResult();
        if(result instanceof Map) {
            Map<String, Object> resultMap = (Map<String, Object>) result;
            Object body = resultMap.get("body");
            if(body instanceof Map) {
                applyResult(videoJob, (Map<String, Object>) body);
            }
        }

        return videoJob;
    }

    @SuppressWarnings("unchecked")
    private void applyResult(VideoJob videoJob, Map<String, Object> result) {
        if(result.get("status") != null) {
            videoJob.setStatus(String.valueOf(result.get("status")));
        }
        if(result.get("boundFileId") != null) {
            videoJob.setBoundFileId(String.valueOf(result.get("boundFileId")));
        }
        if(result.get("usage") != null) {
            videoJob.setUsage(toVideoUsage(result.get("usage")));
        }
        if(result.get("actualSeconds") != null) {
            videoJob.setActualSeconds(toDouble(result.get("actualSeconds")));
        }
        if(result.get("size") != null) {
            videoJob.setSize(String.valueOf(result.get("size")));
        }
        Object error = result.get("error");
        if(error instanceof Map) {
            videoJob.setError(JacksonUtils.convertValue((Map<String, Object>) error, OpenapiError.class));
        }
    }

    @SuppressWarnings("unchecked")
    private VideoUsage toVideoUsage(Object usage) {
        if(usage instanceof VideoUsage) {
            return (VideoUsage) usage;
        }
        if(usage instanceof Map) {
            return JacksonUtils.convertValue((Map<String, Object>) usage, VideoUsage.class);
        }
        return null;
    }

    private Double toDouble(Object value) {
        if(value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("Invalid video actualSeconds: {}", value);
            return null;
        }
    }
}
