package com.ke.bella.queue;

import com.ke.bella.openapi.protocol.BellaEventSourceListener;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.queue.Put;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class QueueClient {

    private final String url;

    private static volatile QueueClient INSTANCE;

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
        HttpUtils.streamRequest(request, listener, put.getTimeout(), put.getTimeout());
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

}
