package com.ke.bella.queue.remote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import com.ke.bella.openapi.BellaResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@SuppressWarnings("all")
public class QueueClient {

    private static final String TAKE_ENDPOINT = "/v1/queue/take";
    private static final String COMPLETE_ENDPOINT = "/v1/queue/%s/complete";
    private static final String BEARER_PREFIX = "Bearer ";

    private final String url;
    private static volatile QueueClient INSTANCE;

    private QueueClient(String url) {
        this.url = url;
    }

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

    public Map<String, List<QueueOps.Task>> take(QueueOps.Take takeRequest, String apiKey) {
        Request request = buildRequest(TAKE_ENDPOINT, apiKey, takeRequest);

        BellaResponse<Map<String, List<QueueOps.Task>>> response = HttpUtils.httpRequest(request,
                new TypeReference<BellaResponse<Map<String, List<QueueOps.Task>>>>() {
                });

        return Optional.ofNullable(response)
                .map(BellaResponse::getData)
                .orElse(Maps.newHashMap());
    }

    public String complete(String taskId, Object result, String apiKey) {
        String endpoint = String.format(COMPLETE_ENDPOINT, taskId);
        Request request = buildRequest(endpoint, apiKey, result);

        BellaResponse<String> response = HttpUtils.httpRequest(request, new TypeReference<BellaResponse<String>>() {
        });

        return Optional.ofNullable(response)
                .map(BellaResponse::getData)
                .orElse(null);
    }

    private Request buildRequest(String endpoint, String apiKey, Object payload) {
        String json = JacksonUtils.serialize(payload);
        MediaType mediaType = MediaType.get("application/json");
        RequestBody body = RequestBody.create(mediaType, json);
        return new Request.Builder()
                .url(url + endpoint)
                .post(body)
                .addHeader(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + apiKey)
                .build();
    }

}
