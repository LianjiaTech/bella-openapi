package com.ke.bella.openapi.protocol.video;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Wan3VideoConverter {

    public static ChannelVideoResult convertFromWanQuery(String channelVideoId, Wan3VideoResponse response) {
        if(response == null || response.getOutput() == null || StringUtils.isBlank(response.getOutput().getTaskStatus())) {
            throw new BellaException.ChannelException(502, "Wan 3.0 video query API returned empty output or task_status");
        }
        Wan3VideoResponse.Output output = response.getOutput();
        String status = mapWanStatus(output.getTaskStatus());
        ChannelVideoResult.ChannelVideoResultBuilder builder = ChannelVideoResult.builder()
                .channelVideoId(channelVideoId)
                .status(status);

        Wan3VideoResponse.Usage usage = response.getUsage();
        if(usage != null) {
            VideoUsage videoUsage = new VideoUsage();
            BeanUtils.copyProperties(usage, videoUsage);
            videoUsage.setInputSeconds(usage.getInputVideoDuration());
            videoUsage.setOutputSeconds(usage.getOutputVideoDuration());
            videoUsage.setRatio(usage.getRatio());
            if(usage.getSr() != null) {
                videoUsage.setSr(String.valueOf(usage.getSr()));
            }
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

    public static String resolveVideoUrl(Wan3VideoResponse response) {
        Wan3VideoResponse.Output output = response.getOutput();
        if(StringUtils.isNotBlank(output.getVideoUrl())) {
            return output.getVideoUrl();
        }
        return output.getWatermarkVideoUrl();
    }

    private static String mapWanStatus(String wanStatus) {
        switch (wanStatus.toUpperCase()) {
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
            log.warn("Unknown Wan 3.0 video status: {}", wanStatus);
            return Status.queued.name();
        }
    }
}
