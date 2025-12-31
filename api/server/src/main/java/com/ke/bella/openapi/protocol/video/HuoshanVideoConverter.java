package com.ke.bella.openapi.protocol.video;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HuoshanVideoConverter {

    public static HuoshanVideoRequest convertToHuoshanRequest(
            VideoCreateRequest openaiRequest,
            HuoshanProperty property) {

        List<HuoshanVideoRequest.Content> contentList;

        if (openaiRequest.getExtra_body() != null &&
            openaiRequest.getExtra_body().containsKey("content")) {
            contentList = parseContentFromExtraBody(openaiRequest.getExtra_body());
        } else {
            contentList = buildContentFromStandardFields(openaiRequest);
        }

        return HuoshanVideoRequest.builder()
                .model(openaiRequest.getModel())
                .content(contentList)
                .return_last_frame(false)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static List<HuoshanVideoRequest.Content> parseContentFromExtraBody(Map<String, Object> extraBody) {
        Object contentObj = extraBody.get("content");
        if (contentObj == null) {
            throw new IllegalArgumentException("content field is null in extra_body");
        }

        List<HuoshanVideoRequest.Content> result = new ArrayList<>();

        try {
            String contentJson = JacksonUtils.serialize(contentObj);
            List<Map<String, Object>> contentArray = JacksonUtils.deserialize(
                contentJson,
                List.class
            );

            for (Map<String, Object> item : contentArray) {
                HuoshanVideoRequest.Content.ContentBuilder builder =
                    HuoshanVideoRequest.Content.builder();

                String type = (String) item.get("type");
                builder.type(type);

                if ("text".equals(type)) {
                    builder.text((String) item.get("text"));
                } else if ("image_url".equals(type)) {
                    Object imageUrlObj = item.get("image_url");
                    if (imageUrlObj != null) {
                        String imageJson = JacksonUtils.serialize(imageUrlObj);
                        HuoshanVideoRequest.ImageUrl imageUrl =
                            JacksonUtils.deserialize(imageJson,
                                HuoshanVideoRequest.ImageUrl.class);
                        builder.image_url(imageUrl);
                    }

                    String role = (String) item.get("role");
                    if (role != null) {
                        builder.role(role);
                    }
                }

                result.add(builder.build());
            }

        } catch (Exception e) {
            log.error("Failed to parse content from extra_body", e);
            throw new IllegalArgumentException("Invalid content format in extra_body: " + e.getMessage());
        }

        return result;
    }

    private static List<HuoshanVideoRequest.Content> buildContentFromStandardFields(VideoCreateRequest request) {
        List<HuoshanVideoRequest.Content> contentList = new ArrayList<>();

        String textContent = buildTextContent(request);
        HuoshanVideoRequest.Content textItem = HuoshanVideoRequest.Content.builder()
                .type("text")
                .text(textContent)
                .build();
        contentList.add(textItem);

        if (request.getInput_reference() != null && !request.getInput_reference().isEmpty()) {
            HuoshanVideoRequest.Content imageItem = HuoshanVideoRequest.Content.builder()
                    .type("image_url")
                    .image_url(HuoshanVideoRequest.ImageUrl.builder()
                            .url(request.getInput_reference())
                            .build())
                    .role("first_frame")
                    .build();
            contentList.add(imageItem);
        }

        return contentList;
    }

    private static String buildTextContent(VideoCreateRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getPrompt());

        List<String> params = new ArrayList<>();

        if (request.getSize() != null) {
            String resolution = convertSizeToResolution(request.getSize());
            if (resolution != null) {
                params.add("--rs " + resolution);
            }

            String ratio = convertSizeToRatio(request.getSize());
            if (ratio != null) {
                params.add("--rt " + ratio);
            }
        }

        boolean durationHandled = false;
        if (request.getExtra_body() != null) {
            Object framesObj = request.getExtra_body().get("frames");
            Object durationObj = request.getExtra_body().get("duration");

            if (framesObj != null) {
                try {
                    int frames = ((Number) framesObj).intValue();
                    if (frames >= 29 && frames <= 289 && (frames - 25) % 4 == 0) {
                        params.add("--frames " + frames);
                        durationHandled = true;
                    } else {
                        log.warn("Invalid frames {} (must be in [29,289] and match 25+4n format)", frames);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse frames from extra_body", e);
                }
            } else if (durationObj != null) {
                try {
                    int duration = ((Number) durationObj).intValue();
                    if (duration >= 2 && duration <= 12) {
                        params.add("--dur " + duration);
                        durationHandled = true;
                    } else {
                        log.warn("Duration {} from extra_body out of range [2,12]", duration);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse duration from extra_body", e);
                }
            }
        }

        if (!durationHandled && request.getSeconds() != null) {
            try {
                int duration = Integer.parseInt(request.getSeconds());
                if (duration >= 2 && duration <= 12) {
                    params.add("--dur " + duration);
                } else {
                    log.warn("Duration {} out of range [2,12], using default 4", duration);
                    params.add("--dur 4");
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid duration format: {}, using default 4", request.getSeconds());
                params.add("--dur 4");
            }
        } else if (!durationHandled) {
            params.add("--dur 4");
        }

        if (!params.isEmpty()) {
            sb.append(" ").append(String.join(" ", params));
        }

        return sb.toString();
    }

    private static String convertSizeToResolution(String size) {
        if (size == null) {
            return null;
        }

        size = size.toLowerCase().trim();

        if (size.contains("1920") || size.contains("1080")) {
            return "1080p";
        }
        if (size.contains("1280") || size.contains("720")) {
            return "720p";
        }
        if (size.contains("640") || size.contains("480")) {
            return "480p";
        }

        if (size.equals("1080p") || size.equals("720p") || size.equals("480p")) {
            return size;
        }

        return null;
    }

    private static String convertSizeToRatio(String size) {
        if (size == null) {
            return null;
        }

        size = size.toLowerCase().trim();

        if (size.equals("1920x1080") || size.equals("1280x720") || size.equals("16:9")) {
            return "16:9";
        }
        if (size.equals("1080x1920") || size.equals("720x1280") || size.equals("9:16")) {
            return "9:16";
        }
        if (size.equals("1024x768") || size.equals("4:3")) {
            return "4:3";
        }
        if (size.equals("768x1024") || size.equals("3:4")) {
            return "3:4";
        }
        if (size.equals("1080x1080") || size.equals("1:1")) {
            return "1:1";
        }
        if (size.equals("2560x1080") || size.equals("21:9")) {
            return "21:9";
        }

        return "16:9";
    }

    public static ChannelVideoResult convertFromHuoshanQuery(HuoshanVideoQueryResponse queryResponse) {
        ChannelVideoResult.ChannelVideoResultBuilder builder = ChannelVideoResult.builder()
                .channelVideoId(queryResponse.getId())
                .status(mapHuoshanStatus(queryResponse.getStatus()));

        if (queryResponse.getDuration() != null && queryResponse.getDuration() > 0) {
            builder.actualSeconds(queryResponse.getDuration().doubleValue());
        }

        if (queryResponse.getResolution() != null && !queryResponse.getResolution().isEmpty()) {
            String standardSize = convertResolutionAndRatioToSize(
                queryResponse.getResolution(),
                queryResponse.getRatio()
            );
            if (standardSize != null) {
                builder.size(standardSize);
            }
        }

        if (queryResponse.getUsage() != null) {
            VideoUsage usage = VideoUsage.builder()
                    .completion_tokens(queryResponse.getUsage().getCompletion_tokens())
                    .prompt_tokens(0)
                    .total_tokens(queryResponse.getUsage().getTotal_tokens())
                    .build();
            builder.usage(usage);
        }

        return builder.build();
    }

    private static String convertResolutionAndRatioToSize(String resolution, String ratio) {
        if (resolution == null) {
            return null;
        }

        String res = resolution.toLowerCase().trim();
        String rat = (ratio != null) ? ratio.trim() : "16:9";

        if ("1080p".equals(res)) {
            if ("9:16".equals(rat)) {
                return "1080x1920";
            } else {
                return "1920x1080";
            }
        } else if ("720p".equals(res)) {
            if ("9:16".equals(rat)) {
                return "720x1280";
            } else {
                return "1280x720";
            }
        } else if ("480p".equals(res)) {
            if ("9:16".equals(rat)) {
                return "480x640";
            } else {
                return "640x480";
            }
        }

        log.warn("Unknown Huoshan resolution or ratio: resolution={}, ratio={}", resolution, ratio);
        return null;
    }

    private static String mapHuoshanStatus(String huoshanStatus) {
        if (huoshanStatus == null) {
            return Status.queued.name();
        }

        switch (huoshanStatus.toLowerCase()) {
        case "queued":
        case "pending":
        case "waiting":
            return Status.queued.name();
        case "running":
        case "processing":
            return Status.processing.name();
        case "succeeded":
        case "success":
        case "completed":
            return Status.completed.name();
        case "failed":
        case "error":
            return Status.failed.name();
        case "expired":
        case "cancelled":
            return Status.cancelled.name();
        default:
            log.warn("Unknown huoshan status: {}", huoshanStatus);
            return Status.queued.name();
        }
    }
}
