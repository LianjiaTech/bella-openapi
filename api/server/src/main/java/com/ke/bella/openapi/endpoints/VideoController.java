package com.ke.bella.openapi.endpoints;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ke.bella.openapi.BellaContext;
import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.ChannelException;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.protocol.OpenapiListResponse;
import com.ke.bella.openapi.protocol.video.VideoCreateRequest;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import com.ke.bella.openapi.protocol.video.VideoRemixRequest;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.service.VideoService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.ModelDB;
import com.ke.bella.openapi.tables.pojos.VideoJobDB;
import com.theokanning.openai.file.File;
import com.theokanning.openai.file.FileUrl;
import com.theokanning.openai.service.OpenAiService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@EndpointAPI
@RestController
@RequestMapping("/v1/videos")
@Tag(name = "videos")
@Slf4j
public class VideoController {

    @Autowired
    VideoService vs;

    @Autowired
    ModelService modelService;

    @Autowired
    ChannelService channelService;

    @Autowired
    OpenAiServiceFactory openAiServiceFactory;

    @Autowired
    OpenapiProperties openapiProperties;

    private OpenAiService videoFileService;

    private static final int VIDEO_FILE_CONNECT_TIMEOUT = 60;
    private static final int VIDEO_FILE_READ_TIMEOUT = 600;

    @PostConstruct
    public void init() {
        videoFileService = openAiServiceFactory.create(
                openapiProperties.getServiceAk(),
                VIDEO_FILE_CONNECT_TIMEOUT,
                VIDEO_FILE_READ_TIMEOUT);
        log.info("[VideoJob] Created video file service for controller with timeout: connect={}s, read={}s",
                VIDEO_FILE_CONNECT_TIMEOUT, VIDEO_FILE_READ_TIMEOUT);
    }

    @PostMapping(consumes = "multipart/form-data")
    public VideoJob createVideo(
            @RequestParam("prompt") String prompt,
            @RequestParam("model") String model,
            @RequestParam(value = "input_reference", required = false) MultipartFile inputReference,
            @RequestParam(value = "seconds", required = false) String seconds,
            @RequestParam(value = "size", required = false) String size) {

        Assert.hasText(prompt, "prompt is required");
        Assert.hasText(model, "model is required");

        if(seconds != null) {
            try {
                int sec = Integer.parseInt(seconds);
                Assert.isTrue(sec >= 1 && sec <= 12,
                        "seconds must be between 1 and 10");
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("seconds must be a valid integer: " + seconds);
            }
        }

        if(size != null) {
            List<String> validSizes = Arrays.asList("1920x1080", "1280x720", "1080x1920");
            Assert.isTrue(validSizes.contains(size),
                    "size must be one of: " + String.join(", ", validSizes));
        }

        String inputReferenceFileId = null;
        if(inputReference != null && !inputReference.isEmpty()) {
            try {
                File file = videoFileService.uploadFile(
                        "temp",
                        inputReference.getBytes(),
                        inputReference.getOriginalFilename());
                inputReferenceFileId = file.getId();
                log.info("[VideoJob] Uploaded input_reference file: {} -> {}", inputReference.getOriginalFilename(), inputReferenceFileId);
            } catch (Exception e) {
                log.error("[VideoJob] Failed to upload input_reference file", e);
                throw new IllegalStateException("failed to upload input_reference file: " + e.getMessage());
            }
        }

        VideoCreateRequest request = VideoCreateRequest.builder()
                .prompt(prompt)
                .model(model)
                .input_reference(inputReferenceFileId)
                .seconds(seconds)
                .size(size)
                .build();

        ModelDB modelDB = modelService.getOne(model);
        Assert.notNull(modelDB, "model not found: " + model);

        List<ChannelDB> channels = channelService.listActives("model", model);
        if(CollectionUtils.isEmpty(channels)) {
            throw new ChannelException.OpenAIException(503, "no_channel",
                    "No channel available for model: " + model);
        }

        String spaceCode = EndpointContext.getApikey().getOwnerCode();
        String akCode = BellaContext.getApikey().getCode();

        VideoJobDB videoJobDB = vs.createVideoJob(request, spaceCode, akCode);

        return transferToVideoJob(videoJobDB);
    }

    @GetMapping("/{id}")
    public VideoJob retrieveVideo(@PathVariable("id") String id) {
        Assert.hasText(id, "video_id is required");

        VideoJobDB videoJobDB = vs.queryVideoJob(id);
        if(videoJobDB == null) {
            throw new ResourceNotFoundException("video not found: " + id);
        }
        return transferToVideoJob(videoJobDB);
    }

    @GetMapping("/{id}/content")
    public void retrieveVideoContent(
            @PathVariable("id") String videoId,
            @RequestParam(required = false) String variant,
            HttpServletResponse response) {
        Assert.hasText(videoId, "video_id is required");

        VideoJobDB videoJobDB = vs.queryVideoJob(videoId);
        if(videoJobDB == null) {
            throw new ResourceNotFoundException("video not found: " + videoId);
        }

        Assert.isTrue(Status.completed.name().equals(videoJobDB.getStatus()),
                "video status must be completed, current status: " + videoJobDB.getStatus());

        String fileId = videoJobDB.getBoundFileId();
        Assert.hasText(fileId, "bound_file_id is empty for video: " + videoId);

        FileUrl fileUrl = videoFileService.retrieveFileUrl(fileId);

        String redirectUrl = fileUrl.getUrl();
        response.setHeader(HttpHeaders.LOCATION, redirectUrl);
        response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
    }

    @PostMapping("/{id}/remix")
    public VideoJob remixVideo(@PathVariable("id") String id, @RequestBody VideoRemixRequest request) {
        throw new UnsupportedOperationException("operation `remix` is not supported currently");
    }

    @GetMapping
    public OpenapiListResponse<VideoJob> listVideos(
            @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String order) {
        if(limit == null) {
            limit = 20;
        }
        Assert.isTrue(limit >= 1, "limit " + limit + " is less than the minimum of 1");
        Assert.isTrue(limit <= 100, "limit " + limit + " is greater than the maximum of 100");

        if(order == null) {
            order = "desc";
        }
        Assert.isTrue("desc".equalsIgnoreCase(order) || "asc".equalsIgnoreCase(order),
                "order " + order + " is not one of ['asc', 'desc']");

        String spaceCode = EndpointContext.getApikey().getOwnerCode();

        List<VideoJobDB> videoJobDBs = vs.listVideoJobs(spaceCode, after, limit + 1, order);
        List<VideoJob> videoJobs = videoJobDBs.stream()
                .map(this::transferToVideoJob)
                .collect(Collectors.toList());

        OpenapiListResponse<VideoJob> response = new OpenapiListResponse<>();

        if(videoJobs.size() > limit) {
            videoJobs = videoJobs.subList(0, limit);
            response.setHasMore(true);
            response.setLastId(videoJobs.get(limit - 1).getId());
        } else if(!videoJobs.isEmpty()) {
            response.setLastId(videoJobs.get(videoJobs.size() - 1).getId());
        }

        response.setData(videoJobs);
        return response;
    }

    @DeleteMapping("/{id}")
    public VideoJob deleteVideo(@PathVariable("id") String id) {
        Assert.hasText(id, "video_id is required");

        VideoJobDB videoJobDB = vs.deleteVideoJob(id);
        if(videoJobDB == null) {
            throw new ResourceNotFoundException("video not found: " + id);
        }

        return transferToVideoJob(videoJobDB);
    }

    private VideoJob transferToVideoJob(VideoJobDB videoJobDB) {
        VideoJob videoJob = new VideoJob();
        videoJob.setId(videoJobDB.getVideoId());
        videoJob.setModel(videoJobDB.getModel());
        videoJob.setStatus(videoJobDB.getStatus());
        videoJob.setProgress(videoJobDB.getProgress());
        if(videoJobDB.getCompletedAt() != null) {
            videoJob.setCompleted_at((int) (videoJobDB.getCompletedAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()));
        }
        if(videoJobDB.getExpiresAt() != null) {
            videoJob.setExpires_at((int) (videoJobDB.getExpiresAt().atZone(java.time.ZoneId.systemDefault()).toEpochSecond()));
        }
        videoJob.setPrompt(videoJobDB.getPrompt());
        if(videoJobDB.getSeconds() != null && videoJobDB.getSeconds() > 0) {
            double seconds = videoJobDB.getSeconds() / 1000.0;
            videoJob.setSeconds(String.valueOf(seconds));
        }
        if(!StringUtils.isEmpty(videoJob.getSize())) {
            videoJob.setSize(videoJobDB.getSize());
        }
        if(videoJobDB.getRemixedFromVideoId() != null &&
                !videoJobDB.getRemixedFromVideoId().isEmpty() &&
                !"0".equals(videoJobDB.getRemixedFromVideoId())) {
            videoJob.setRemixed_from_video_id(videoJobDB.getRemixedFromVideoId());
        }

        return videoJob;
    }
}
