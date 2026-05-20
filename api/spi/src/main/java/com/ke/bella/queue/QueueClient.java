package com.ke.bella.queue;

import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.queue.Put;
import com.theokanning.openai.queue.Task;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Slf4j
public class QueueClient {

    private final String url;

    private static volatile QueueClient INSTANCE;

    private static final int DEFAULT_TIMEOUT_SECONDS = 600;

    public static QueueClient getInstance(String url) {
        if(INSTANCE == null) {
            synchronized(QueueClient.class) {
                if(INSTANCE == null) {
                    INSTANCE = new QueueClient(url);
                }
            }
        }
        return INSTANCE;
    }

    private QueueClient(String url) {
        if(StringUtils.isBlank(url)) {
            throw new IllegalStateException("Queue Service URL is not configured.");
        }
        this.url = url;
    }

    public Task put(Put put, String ak) {
        String putUrl = url + "/v1/queue/put";
        Request request = buildRequest(putUrl, ak, JacksonUtils.serialize(put));
        return toTask(HttpUtils.httpRequest(request, Map.class));
    }

    public <T> T put(Put put, String ak, Class<T> clazz) {
        String putUrl = url + "/v1/queue/put";
        Request request = buildRequest(putUrl, ak, JacksonUtils.serialize(put));
        return HttpUtils.httpRequest(request, clazz);
    }

    public <T> T blockingPut(Put put, String ak, Class<T> clazz, Callbacks.ChannelErrorCallback<T> errorCallback) {
        put.setResponseMode("blocking");
        String putUrl = url + "/v1/queue/put";
        Request request = buildRequest(putUrl, ak, JacksonUtils.serialize(put));
        return HttpUtils.httpRequest(request, clazz, errorCallback);
    }

    public void streamingPut(Put put, String ak, BellaEventSourceListener listener) {
        put.setResponseMode("streaming");
        String putUrl = url + "/v1/queue/put";
        Request request = buildRequest(putUrl, ak, JacksonUtils.serialize(put));
        int timeout = (put.getTimeout() != null && put.getTimeout() > 0)
                ? put.getTimeout()
                : DEFAULT_TIMEOUT_SECONDS;
        HttpUtils.streamRequest(request, listener, timeout, timeout);
    }

    public Task getTaskDetail(String taskId, String apikey) {
        String resultUrl = url + "/v1/queue/" + taskId;
        Request request = buildGetRequest(resultUrl, apikey);
        return toTask(HttpUtils.httpRequest(request, Map.class));
    }

    public Request buildRequest(String url, String apikey, String json) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(requestBody);
        if(apikey != null) {
            builder.header("Authorization", "Bearer " + apikey);
        }
        return builder.build();
    }

    public Request buildGetRequest(String url, String apikey) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .get();
        if(apikey != null) {
            builder.header("Authorization", "Bearer " + apikey);
        }
        return builder.build();
    }

    private Task toTask(Map<?, ?> response) {
        if(response == null) {
            return null;
        }
        Object data = response.get("data");
        Task task = JacksonUtils.deserialize(JacksonUtils.serialize(response), Task.class);
        if(task == null) {
            task = new Task();
        }
        task.setResult(response.get("result"));
        if(task.getTaskId() != null) {
            return task;
        }
        if(data instanceof String) {
            task.setTaskId((String) data);
        }
        return task;
    }

    public Object getOutputData(Task task) {
        return task == null ? null : task.getResult();
    }

}
