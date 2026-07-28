package com.ke.bella.openapi.protocol.video;

import java.util.UUID;

import com.ke.bella.openapi.protocol.IProtocolAdaptor;
import com.theokanning.openai.file.File;
import com.theokanning.openai.service.OpenAiService;

public interface VideoAdaptor<T extends VideoProperty> extends IProtocolAdaptor {

    String submitVideoTask(
            VideoCreateRequest request,
            String baseUrl,
            T property,
            String videoId);

    ChannelVideoResult queryVideoTask(
            String channelVideoId,
            String baseUrl,
            T property);

    File transferVideoToFile(
            String channelVideoId,
            String baseUrl,
            T property,
            OpenAiService openAiService);

    default String buildVideoFileName() {
        return "video-" + UUID.randomUUID() + ".mp4";
    }

    @Override
    default String endpoint() {
        return "/v1/videos";
    }
}
