package com.ke.bella.openapi.protocol.video;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class QwenVideoConverter {

    public static ChannelVideoResult convertFromQwenQuery(String channelVideoId, QwenVideoResponse response) {
        if(response == null || response.getOutput() == null || StringUtils.isBlank(response.getOutput().getTaskStatus())) {
            throw new BellaException.ChannelException(502, "Qwen video query API returned empty output or task_status");
        }
        QwenVideoResponse.Output output = response.getOutput();
        String status = mapQwenStatus(output.getTaskStatus());
        ChannelVideoResult.ChannelVideoResultBuilder builder = ChannelVideoResult.builder()
                .channelVideoId(channelVideoId)
                .status(status);

        QwenVideoResponse.Usage usage = response.getUsage();
        if(usage != null) {
            VideoUsage videoUsage = new VideoUsage();
            BeanUtils.copyProperties(usage, videoUsage);
            builder.usage(videoUsage);
            if(usage.getDuration() != null && usage.getDuration() > 0) {
                builder.actualSeconds(usage.getDuration());
            }
            if(StringUtils.isNotBlank(usage.getSize())) {
                builder.size(usage.getSize());
            }
        }

        if(Status.failed.name().equals(status)) {
            builder.error(ChannelVideoResult.ErrorInfo.builder()
                    .code(output.getCode())
                    .message(output.getMessage())
                    .build());
        }

        return builder.build();
    }

    public static String resolveVideoUrl(QwenVideoResponse response) {
        QwenVideoResponse.Output output = response.getOutput();
        if(StringUtils.isNotBlank(output.getVideoUrl())) {
            return output.getVideoUrl();
        }
        return output.getWatermarkVideoUrl();
    }

    private static String mapQwenStatus(String qwenStatus) {
        switch (qwenStatus.toUpperCase()) {
        case "PENDING":
            return Status.queued.name();
        case "RUNNING":
            return Status.processing.name();
        case "SUCCEEDED":
            return Status.completed.name();
        case "FAILED":
        case "UNKNOWN":
            return Status.failed.name();
        case "CANCELED":
            return Status.cancelled.name();
        default:
            log.warn("Unknown qwen video status: {}", qwenStatus);
            return Status.queued.name();
        }
    }
}
