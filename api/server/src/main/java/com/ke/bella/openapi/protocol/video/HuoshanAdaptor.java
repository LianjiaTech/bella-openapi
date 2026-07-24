package com.ke.bella.openapi.protocol.video;

import org.springframework.stereotype.Component;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.utils.HttpUtils;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

@Slf4j
@Component("HuoshanVideo")
public class HuoshanAdaptor implements VideoAdaptor<HuoshanProperty> {

    @Override
    public String submitVideoTask(
            VideoCreateRequest request,
            String baseUrl,
            HuoshanProperty property,
            String videoId) {
        HuoshanVideoRequest huoshanRequest = HuoshanVideoConverter.convertToHuoshanRequest(
                request,
                property);

        Request httpRequest = buildHttpRequest(baseUrl, huoshanRequest, property);

        HuoshanVideoResponse response = HttpUtils.httpRequest(
                httpRequest,
                HuoshanVideoResponse.class);

        if(response.getCode() != null && response.getCode() != 0) {
            throw new BellaException.ChannelException(502,
                    "Huoshan API error: code=" + response.getCode() +
                            ", message=" + response.getMessage());
        }

        String channelVideoId = response.getId();
        if(channelVideoId == null) {
            throw new BellaException.ChannelException(502, "Huoshan API returned null id");
        }

        return channelVideoId;
    }

    @Override
    public ChannelVideoResult queryVideoTask(
            String channelVideoId,
            String baseUrl,
            HuoshanProperty property) {
        String queryUrl = baseUrl + "/" + channelVideoId;

        Request httpRequest = buildHttpGetRequest(queryUrl, property);

        HuoshanVideoQueryResponse response = HttpUtils.httpRequest(
                httpRequest,
                HuoshanVideoQueryResponse.class);

        if(response.getCode() != null && response.getCode() != 0) {
            throw new BellaException.ChannelException(502,
                    "Huoshan query API error: code=" + response.getCode() +
                            ", message=" + response.getMessage());
        }

        return HuoshanVideoConverter.convertFromHuoshanQuery(response);
    }

    @Override
    public com.theokanning.openai.file.File transferVideoToFile(
            String channelVideoId,
            String baseUrl,
            HuoshanProperty property,
            com.theokanning.openai.service.OpenAiService openAiService) {
        String queryUrl = baseUrl + "/" + channelVideoId;
        Request httpRequest = buildHttpGetRequest(queryUrl, property);

        HuoshanVideoQueryResponse response = HttpUtils.httpRequest(
                httpRequest,
                HuoshanVideoQueryResponse.class);

        if(response.getCode() != null && response.getCode() != 0) {
            throw new BellaException.ChannelException(502,
                    "Huoshan query API error: code=" + response.getCode() +
                            ", message=" + response.getMessage());
        }

        if(response.getContent() == null || response.getContent().getVideo_url() == null) {
            throw new BellaException.ChannelException(502, "Video URL not found in Huoshan response");
        }

        String videoUrl = response.getContent().getVideo_url();

        try (java.io.InputStream videoStream = HttpUtils.downloadStream(videoUrl)) {
            com.theokanning.openai.file.File file = openAiService.uploadFile(
                    "temp",
                    videoStream,
                    "video.mp4");

            return file;

        } catch (java.io.IOException e) {
            log.error("[HuoshanAdaptor] Failed to transfer video: videoUrl={}", videoUrl, e);
            throw new BellaException.ChannelException(502, "Failed to transfer video: " + e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "火山方舟视频生成";
    }

    @Override
    public Class<?> getPropertyClass() {
        return HuoshanProperty.class;
    }

    private Request buildHttpRequest(
            String url,
            HuoshanVideoRequest request,
            HuoshanProperty property) {
        byte[] requestBytes = JacksonUtils.toByte(request);
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                requestBytes);

        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .post(body)
                .header("Content-Type", "application/json");

        return builder.build();
    }

    private Request buildHttpGetRequest(
            String url,
            HuoshanProperty property) {
        Request.Builder builder = authorizationRequestBuilder(property.getAuth())
                .url(url)
                .get();

        return builder.build();
    }
}
