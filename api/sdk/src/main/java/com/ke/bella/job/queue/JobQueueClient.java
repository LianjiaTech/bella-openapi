package com.ke.bella.job.queue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ke.bella.job.queue.api.entity.param.TaskParam;
import com.ke.bella.job.queue.api.entity.response.TaskResp;
import com.ke.bella.job.queue.api.enums.ResponseModeEnum;
import com.ke.bella.job.queue.api.enums.TaskStatusEnum;
import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.BellaStreamCallback;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class JobQueueClient {

    private final String url;

    private static volatile JobQueueClient INSTANCE;

    public static JobQueueClient getInstance(String url) {
        if(INSTANCE == null) {
            INSTANCE = new JobQueueClient(url);
        }
        return INSTANCE;
    }

    public JobQueueClient(String url) {
        if (url == null) {
            throw new IllegalStateException("Queue Service URL is not configured.");
        }
        this.url = url;
    }

    public <T> T put(TaskParam.TaskPutParam task, String ak, Class<T> type) {
        String putUrl = url + "/v1/tasks/put";
        Request request = getPostRequest(putUrl, ak, JacksonUtils.serialize(task));
        return HttpUtils.httpRequest(request, type);
    }

    public <T> T blockingPut(TaskParam.TaskPutParam task, String ak, Class<T> type, Callbacks.ChannelErrorCallback<T> errorCallback) {
        task.setModel(ResponseModeEnum.BLOCKING.getModeString());
        String putUrl = url + "/v1/tasks/put";
        Request request = getPostRequest(putUrl, ak, JacksonUtils.serialize(task));
        return HttpUtils.httpRequest(request, type, errorCallback);
    }

    public void streamPut(TaskParam.TaskPutParam task, String ak, BellaEventSourceListener listener) {
        task.setModel(ResponseModeEnum.STREAM.getModeString());
        String putUrl = url + "/v1/tasks/put";
        Request request = getPostRequest(putUrl, ak, JacksonUtils.serialize(task));
        HttpUtils.streamRequest(request, listener, task.getTimeout(), task.getTimeout());
    }

    public TaskResp.TaskGetResp poll(int pollSize, String endpoint, String queueName) {
        try {
            String pollUrl = url + "/v1/tasks/get";
            TaskParam.TaskGetParam taskGetParam = new TaskParam.TaskGetParam();
            taskGetParam.setEndpoint(endpoint);
            taskGetParam.setModels(Collections.singletonList(queueName));
            taskGetParam.setSize(pollSize);
            taskGetParam.setLevel(1);
            Request request = getPostRequest(pollUrl, null, JacksonUtils.serialize(taskGetParam));
            TaskResp.TaskGetResp taskResponse = HttpUtils.httpRequest(request, TaskResp.TaskGetResp.class);
            return Objects.isNull(taskResponse) ? TaskResp.TaskGetResp.builder().build() : taskResponse;
        } catch (Exception e) {
            LOGGER.error("fetch job-queue task failed", e);
            return TaskResp.TaskGetResp.builder().build();
        }
    }

    public void update(String taskId, TaskStatusEnum taskStatus, String output) {
        String updateUrl = url + "/v1/tasks/update/status";
        TaskParam.TaskUpdateParam taskUpdateParam = new TaskParam.TaskUpdateParam();
        taskUpdateParam.setTaskId(taskId);
        taskUpdateParam.setStatus(taskStatus.getDescription());
        taskUpdateParam.setOutputData(output);
        Request request = getPostRequest(updateUrl, null, JacksonUtils.serialize(taskUpdateParam));
        TaskResp.TaskUpdateStatusResp updateTaskResponse = HttpUtils.httpRequest(request, TaskResp.TaskUpdateStatusResp.class);
        Assert.isTrue(!StringUtils.isEmpty(updateTaskResponse.getTaskId()), "update task failed");
    }

    public TaskParam.TaskPutParam buildTaskPutRequest(Object task, Integer timeout, String endpoint, String queueName) {
        if(endpoint == null || queueName == null) {
            throw new BizParamCheckException("invalid params");
        }
        TaskParam.TaskPutParam req = TaskParam.TaskPutParam.builder()
                .data(task)
                .endpoint(endpoint)
                .model(queueName)
                .build();
        if(timeout != null) {
            req.setTimeout(timeout);
        }
        return req;
    }

    public TaskResp.TaskGetDetailResp getTaskDetail(String taskId, String apikey) {
        String resultUrl = url + "/v1/tasks/get/detail";
        TaskParam.TaskGetDetailParam req = TaskParam.TaskGetDetailParam.builder()
                .taskId(taskId)
                .build();
        Request request = getPostRequest(resultUrl, apikey, JacksonUtils.serialize(req));
        return HttpUtils.httpRequest(request, TaskResp.TaskGetDetailResp.class);
    }


    public Request getPostRequest(String url, String apikey, String json) {
        RequestBody requestBody = RequestBody.create(json, MediaType.parse("application/json; charset=utf-8"));
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(requestBody);
        if(apikey != null) {
            builder.header("Authorization", "Bearer " + apikey);
        }
        return builder.build();
    }

}
