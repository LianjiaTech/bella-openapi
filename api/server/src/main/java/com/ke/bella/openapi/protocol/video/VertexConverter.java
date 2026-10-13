package com.ke.bella.openapi.protocol.video;

import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import org.apache.commons.lang3.StringUtils;

public final class VertexConverter {

    private VertexConverter() {
    }

    public static ChannelVideoResult convert(String channelVideoId, VertexResponse response) {
        if(response == null || StringUtils.isBlank(response.getStatus())) {
            throw new BellaException.ChannelException(502, "Vertex returned empty status");
        }
        String status = mapStatus(response.getStatus());
        ChannelVideoResult.ChannelVideoResultBuilder builder = ChannelVideoResult.builder()
                .channelVideoId(channelVideoId)
                .status(status);
        if(response.getUsage() != null) {
            VideoUsage usage = new VideoUsage();
            usage.setPromptTokens(response.getUsage().getTotalInputTokens());
            usage.setCompletionTokens(response.getUsage().getTotalOutputTokens());
            usage.setTextOutputTokens(resolveOutputTokens(response.getUsage(), "TEXT"));
            usage.setVideoOutputTokens(resolveOutputTokens(response.getUsage(), "VIDEO"));
            usage.setTotalTokens(response.getUsage().getTotalTokens());
            builder.usage(usage);
        }
        if(Status.failed.name().equals(status) || Status.cancelled.name().equals(status) || "incomplete".equalsIgnoreCase(response.getStatus())) {
            VertexResponse.ErrorDetail error = resolveError(response);
            String errorMessage = firstNonBlank(
                    error == null ? null : error.getMessage(),
                    response.getMessage());
            builder.error(ChannelVideoResult.ErrorInfo.builder()
                    .code(firstNonBlank(error == null ? null : error.getCode(), response.getCode(), response.getStatus()))
                    .message(StringUtils.defaultIfBlank(errorMessage, fallbackErrorMessage(response)))
                    .build());
        }
        return builder.build();
    }

    public static String resolveVideoUri(VertexResponse response) {
        if(response == null || response.getSteps() == null) {
            return null;
        }
        for(VertexResponse.Step step : response.getSteps()) {
            if(step == null || !"model_output".equals(step.getType()) || step.getContent() == null) {
                continue;
            }
            for(VertexResponse.Content content : step.getContent()) {
                if(content != null && "video".equals(content.getType())) {
                    if(StringUtils.isNotBlank(content.getUri())) {
                        return content.getUri();
                    }
                    if(StringUtils.isNotBlank(content.getData())) {
                        return "data:" + StringUtils.defaultIfBlank(content.getMimeType(), "video/mp4") + ";base64," + content.getData();
                    }
                }
            }
        }
        return null;
    }

    private static String mapStatus(String status) {
        if(status == null) {
            return Status.failed.name();
        }
        switch(status.toLowerCase()) {
        case "created":
        case "in_progress":
            return Status.processing.name();
        case "requires_action":
            return Status.processing.name();
        case "completed":
            return Status.completed.name();
        case "cancelled":
            return Status.cancelled.name();
        case "failed":
        case "incomplete":
        default:
            return Status.failed.name();
        }
    }

    private static VertexResponse.ErrorDetail resolveError(VertexResponse response) {
        if(response.getError() != null) {
            return response.getError();
        }
        if(response.getLastError() != null) {
            return response.getLastError();
        }
        return response.getIncompleteDetails();
    }

    private static Integer resolveOutputTokens(VertexResponse.Usage usage, String modality) {
        if(usage.getOutputTokensByModality() == null) {
            return null;
        }
        return usage.getOutputTokensByModality().stream()
                .filter(item -> modality.equalsIgnoreCase(item.getModality()))
                .findFirst()
                .map(VertexResponse.ModalityTokenCount::getTokenCount)
                .orElse(null);
    }

    private static String firstNonBlank(String... values) {
        for(String value : values) {
            if(StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static String fallbackErrorMessage(VertexResponse response) {
        return "Vertex task failed without error details: id=" + response.getId()
                + ", model=" + response.getModel()
                + ", object=" + response.getObject()
                + ", status=" + response.getStatus();
    }
}
