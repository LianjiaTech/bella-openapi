package com.ke.bella.openapi.protocol.video;

import org.apache.commons.lang3.StringUtils;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MiniMaxVideoConverter {

    public static ChannelVideoResult convertFromMiniMaxQuery(MiniMaxVideoResponse response) {
        if(response == null || response.getTask() == null || StringUtils.isBlank(response.getTask().getStatus())) {
            throw new BellaException.ChannelException(502, "MiniMax video query API returned empty task or status");
        }
        MiniMaxVideoResponse.Task task = response.getTask();
        ChannelVideoResult.ChannelVideoResultBuilder builder = ChannelVideoResult.builder()
                .channelVideoId(task.getId())
                .status(mapStatus(task.getStatus()));

        if(StringUtils.isNotBlank(task.getResolution())) {
            builder.size(task.getResolution());
        }
        if(task.getDuration() != null) {
            builder.actualSeconds(task.getDuration().doubleValue());
        }
        if(task.getUsage() != null) {
            builder.usage(toVideoUsage(task));
        }
        if(task.getError() != null) {
            builder.error(ChannelVideoResult.ErrorInfo.builder()
                    .code(task.getError().getCode())
                    .message(task.getError().getMessage())
                    .build());
        }
        return builder.build();
    }

    public static String resolveVideoUrl(MiniMaxVideoResponse response) {
        if(response == null || response.getTask() == null || response.getTask().getContent() == null) {
            return null;
        }
        return response.getTask().getContent().getUrl();
    }

    private static VideoUsage toVideoUsage(MiniMaxVideoResponse.Task task) {
        MiniMaxVideoResponse.Usage usage = task.getUsage();
        VideoUsage videoUsage = new VideoUsage();
        videoUsage.setDuration(usage.getTotalSeconds());
        videoUsage.setTotalSeconds(usage.getTotalSeconds());
        videoUsage.setInputSeconds(usage.getInputSeconds());
        videoUsage.setOutputSeconds(usage.getOutputSeconds());
        videoUsage.setImageCount(usage.getImageCount() == null ? 0 : usage.getImageCount());
        videoUsage.setInputAudioSeconds(usage.getInputAudioSeconds());
        videoUsage.setTotalTokens(usage.getTotalTokens());
        videoUsage.setPromptTokens(usage.getPromptTokens());
        videoUsage.setCompletionTokens(usage.getCompletionTokens());
        videoUsage.setSr(task.getResolution());
        return videoUsage;
    }

    private static String mapStatus(String status) {
        switch (status.toLowerCase()) {
        case "queued":
            return Status.queued.name();
        case "running":
            return Status.processing.name();
        case "succeeded":
            return Status.completed.name();
        case "failed":
        case "expired":
            return Status.failed.name();
        case "cancelled":
            return Status.cancelled.name();
        default:
            log.warn("Unknown MiniMax video status: {}", status);
            return Status.queued.name();
        }
    }
}
