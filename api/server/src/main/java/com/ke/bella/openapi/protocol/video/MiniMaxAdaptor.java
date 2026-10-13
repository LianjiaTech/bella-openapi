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
@Component("MiniMaxVideo")
public class MiniMaxAdaptor implements VideoAdaptor<MiniMaxProperty> {

    @Override
    public String submitVideoTask(VideoCreateRequest request, String baseUrl, MiniMaxProperty property, String videoId) {
        request.setModel(property.getDeployName());
        Request httpRequest = buildPostRequest(baseUrl, request, property);

        MiniMaxVideoResponse response = HttpUtils.httpRequest(httpRequest, MiniMaxVideoResponse.class, errorCallback);
        if(response == null || StringUtils.isBlank(response.getTaskId())) {
            throw new BellaException.ChannelException(502, "MiniMax video API returned empty task_id");
        }
        return response.getTaskId();
    }

    @Override
    public ChannelVideoResult queryVideoTask(String channelVideoId, String baseUrl, MiniMaxProperty property) {
        MiniMaxVideoResponse response = query(channelVideoId, property);
        return MiniMaxVideoConverter.convertFromMiniMaxQuery(response);
    }

    @Override
    public File transferVideoToFile(String channelVideoId, String baseUrl, MiniMaxProperty property, OpenAiService openAiService) {
        MiniMaxVideoResponse response = query(channelVideoId, property);
        String videoUrl = MiniMaxVideoConverter.resolveVideoUrl(response);
        if(StringUtils.isBlank(videoUrl)) {
            throw new BellaException.ChannelException(502, "Video URL not found in MiniMax response");
        }
        try (InputStream videoStream = HttpUtils.downloadStream(videoUrl)) {
            return openAiService.uploadFile("temp", videoStream, buildVideoFileName());
        } catch (IOException e) {
            log.error("[MiniMaxAdaptor] Failed to transfer video: videoUrl={}", videoUrl, e);
            throw new BellaException.ChannelException(502, "Failed to transfer video: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "MiniMax 视频生成";
    }

    @Override
    public Class<?> getPropertyClass() {
        return MiniMaxProperty.class;
    }

    private MiniMaxVideoResponse query(String channelVideoId, MiniMaxProperty property) {
        Request httpRequest = buildGetRequest(channelVideoId, property);
        return HttpUtils.httpRequest(httpRequest, MiniMaxVideoResponse.class, errorCallback);
    }

    private Request buildPostRequest(String url, VideoCreateRequest request, MiniMaxProperty property) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request));
        return authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .build();
    }

    private Request buildGetRequest(String channelVideoId, MiniMaxProperty property) {
        return authorizationRequestBuilder(property.getAuth())
                .url(buildQueryUrl(channelVideoId, property))
                .get()
                .build();
    }

    private String buildQueryUrl(String channelVideoId, MiniMaxProperty property) {
        if(StringUtils.isBlank(property.getTaskQueryUrl())) {
            throw new IllegalArgumentException("MiniMax video property taskQueryUrl is required");
        }
        return StringUtils.removeEnd(property.getTaskQueryUrl(), "/") + "/" + channelVideoId;
    }

    private final Callbacks.ChannelErrorCallback<MiniMaxVideoResponse> errorCallback = (response, httpResponse) -> {
        if(response != null && response.getError() != null) {
            MiniMaxVideoResponse.ErrorDetail error = response.getError();
            OpenapiResponse.OpenapiError openapiError = OpenapiResponse.OpenapiError.builder()
                    .type(error.getType())
                    .code(error.getHttpCode())
                    .httpCode(httpResponse.code())
                    .message(error.getMessage())
                    .param(response.getRequestId())
                    .build();
            throw new BellaException.ChannelException(httpResponse.code(), error.getType(), error.getMessage(), openapiError);
        }
        throw new BellaException.ChannelException(httpResponse.code(), httpResponse.message());
    };
}
