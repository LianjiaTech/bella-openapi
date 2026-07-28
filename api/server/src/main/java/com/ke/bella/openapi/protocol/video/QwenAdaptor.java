package com.ke.bella.openapi.protocol.video;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.file.File;
import com.theokanning.openai.service.OpenAiService;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Component("QwenVideo")
public class QwenAdaptor implements VideoAdaptor<QwenProperty> {

    @Override
    public String submitVideoTask(VideoCreateRequest request, String baseUrl, QwenProperty property, String videoId) {
        request.setModel(property.getDeployName());
        Request httpRequest = buildPostRequest(baseUrl, request, property);

        QwenVideoResponse response = HttpUtils.httpRequest(httpRequest, QwenVideoResponse.class);
        validateResponse(response, "Qwen video submit API error");

        String taskId = response.getOutput() == null ? null : response.getOutput().getTaskId();
        if(StringUtils.isBlank(taskId)) {
            throw new BellaException.ChannelException(502, "Qwen video API returned empty task_id");
        }
        return taskId;
    }

    @Override
    public ChannelVideoResult queryVideoTask(String channelVideoId, String baseUrl, QwenProperty property) {
        QwenVideoResponse response = query(channelVideoId, property);
        validateResponse(response, "Qwen video query API error");
        return QwenVideoConverter.convertFromQwenQuery(channelVideoId, response);
    }

    @Override
    public File transferVideoToFile(String channelVideoId, String baseUrl, QwenProperty property, OpenAiService openAiService) {
        QwenVideoResponse response = query(channelVideoId, property);
        validateResponse(response, "Qwen video query API error");
        openAiService = new OpenAiService("6e889228-5a50-4a4a-b637-cab96518290e", "https://openapi-ait.ke.com/v1/");
        String videoUrl = QwenVideoConverter.resolveVideoUrl(response);
        if(StringUtils.isBlank(videoUrl)) {
            throw new BellaException.ChannelException(502, "Video URL not found in Qwen response");
        }

        try (InputStream videoStream = HttpUtils.downloadStream(videoUrl)) {
            return openAiService.uploadFile("temp", videoStream, buildVideoFileName());
        } catch (IOException e) {
            log.error("[QwenAdaptor] Failed to transfer video: videoUrl={}", videoUrl, e);
            throw new BellaException.ChannelException(502, "Failed to transfer video: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "阿里云百炼视频生成";
    }

    @Override
    public Class<?> getPropertyClass() {
        return QwenProperty.class;
    }

    private QwenVideoResponse query(String channelVideoId, QwenProperty property) {
        Request httpRequest = buildGetRequest(channelVideoId, property);
        return HttpUtils.httpRequest(httpRequest, QwenVideoResponse.class);
    }

    private Request buildPostRequest(String url, VideoCreateRequest request, QwenProperty property) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), JacksonUtils.toByte(request));
        return authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(body)
                .header("Content-Type", "application/json")
                .header("X-DashScope-Async", "enable")
                .build();
    }

    private Request buildGetRequest(String channelVideoId, QwenProperty property) {
        if(StringUtils.isBlank(property.getTaskQueryUrl())) {
            throw new IllegalArgumentException("Qwen video property taskQueryUrl is required");
        }
        String queryUrl = StringUtils.removeEnd(property.getTaskQueryUrl(), "/") + "/" + channelVideoId;
        return authorizationRequestBuilder(property.getAuth())
                .url(queryUrl)
                .get()
                .build();
    }

    private void validateResponse(QwenVideoResponse response, String prefix) {
        if(StringUtils.isNotBlank(response.getCode())) {
            throw new BellaException.ChannelException(502, prefix + ": code=" + response.getCode() + ", message=" + response.getMessage());
        }
    }
}
