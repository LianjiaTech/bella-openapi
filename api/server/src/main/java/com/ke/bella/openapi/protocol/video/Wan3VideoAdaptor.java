package com.ke.bella.openapi.protocol.video;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.Callbacks;
import com.ke.bella.openapi.protocol.OpenapiResponse;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.file.File;
import com.theokanning.openai.service.OpenAiService;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Component("Wan3Video")
public class Wan3VideoAdaptor implements VideoAdaptor<Wan3VideoProperty> {

    @Override
    public String submitVideoTask(VideoCreateRequest request, String baseUrl, Wan3VideoProperty property, String videoId) {
        if(StringUtils.isNotBlank(property.getDeployName())) {
            request.setModel(property.getDeployName());
        }
        Request httpRequest = buildPostRequest(baseUrl, request, property);

        Wan3VideoResponse response = HttpUtils.httpRequest(httpRequest, Wan3VideoResponse.class, errorCallback);
        validateResponse(response, "Wan 3.0 video submit API error");

        String taskId = response.getOutput() == null ? null : response.getOutput().getTaskId();
        if(StringUtils.isBlank(taskId)) {
            throw new BellaException.ChannelException(502, "Wan 3.0 video API returned empty task_id");
        }
        return taskId;
    }

    @Override
    public ChannelVideoResult queryVideoTask(String channelVideoId, String baseUrl, Wan3VideoProperty property) {
        Wan3VideoResponse response = query(channelVideoId, property);
        validateResponse(response, "Wan 3.0 video query API error");
        return Wan3VideoConverter.convertFromWanQuery(channelVideoId, response);
    }

    @Override
    public File transferVideoToFile(String channelVideoId, String baseUrl, Wan3VideoProperty property, OpenAiService openAiService) {
        Wan3VideoResponse response = query(channelVideoId, property);
        validateResponse(response, "Wan 3.0 video query API error");
        String videoUrl = Wan3VideoConverter.resolveVideoUrl(response);
        if(StringUtils.isBlank(videoUrl)) {
            throw new BellaException.ChannelException(502, "Video URL not found in Wan 3.0 response");
        }

        try (InputStream videoStream = HttpUtils.downloadStream(videoUrl)) {
            return openAiService.uploadFile("temp", videoStream, buildVideoFileName());
        } catch (IOException e) {
            log.error("[Wan3VideoAdaptor] Failed to transfer video: videoUrl={}", videoUrl, e);
            throw new BellaException.ChannelException(502, "Failed to transfer video: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "阿里云万相3.0视频生成";
    }

    @Override
    public Class<?> getPropertyClass() {
        return Wan3VideoProperty.class;
    }

    private Wan3VideoResponse query(String channelVideoId, Wan3VideoProperty property) {
        Request httpRequest = buildGetRequest(channelVideoId, property);
        return HttpUtils.httpRequest(httpRequest, Wan3VideoResponse.class, errorCallback);
    }

    private Request buildPostRequest(String url, VideoCreateRequest request, Wan3VideoProperty property) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request));
        return authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("X-DashScope-Async", "enable")
                .build();
    }

    private Request buildGetRequest(String channelVideoId, Wan3VideoProperty property) {
        if(StringUtils.isBlank(property.getTaskQueryUrl())) {
            throw new IllegalArgumentException("Wan 3.0 video property taskQueryUrl is required");
        }
        String queryUrl = StringUtils.removeEnd(property.getTaskQueryUrl(), "/") + "/" + channelVideoId;
        return authorizationRequestBuilder(property.getAuth())
                .url(queryUrl)
                .get()
                .build();
    }

    private void validateResponse(Wan3VideoResponse response, String prefix) {
        if(response == null) {
            throw new BellaException.ChannelException(502, prefix + ": empty response");
        }
        if(StringUtils.isNotBlank(response.getCode())) {
            throw new BellaException.ChannelException(502, prefix + ": code=" + response.getCode() + ", message=" + response.getMessage());
        }
    }

    private final Callbacks.ChannelErrorCallback<Wan3VideoResponse> errorCallback = (response, httpResponse) -> {
        if(response != null && StringUtils.isNotBlank(response.getCode())) {
            OpenapiResponse.OpenapiError error = OpenapiResponse.OpenapiError.builder()
                    .code(response.getCode())
                    .message(response.getMessage())
                    .httpCode(httpResponse.code())
                    .param(response.getRequestId())
                    .build();
            throw new BellaException.ChannelException(httpResponse.code(), response.getCode(), response.getMessage(), error);
        }
        throw new BellaException.ChannelException(httpResponse.code(), httpResponse.message());
    };
}
