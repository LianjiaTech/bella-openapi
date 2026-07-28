package com.ke.bella.openapi.endpoints;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.EndpointContext;
import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.common.exception.ResourceNotFoundException;
import com.ke.bella.openapi.protocol.video.VideoCreateRequest;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import com.ke.bella.openapi.protocol.video.VideoRemixRequest;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.ke.bella.openapi.service.VideoService;
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

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public VideoJob createVideo(@RequestBody VideoCreateRequest request) {
        Assert.notNull(request, "request is required");
        Assert.hasText(request.getModel(), "model is required");
        Assert.isTrue(request.getExtra_body() != null && !request.getExtra_body().isEmpty(), "request body is required");

        String apikey = EndpointContext.getProcessData().getApikey();
        return vs.createVideoJob(request, apikey);
    }

    @GetMapping("/{id}")
    public VideoJob retrieveVideo(@PathVariable("id") String id) {
        Assert.hasText(id, "video_id is required");

        String apikey = EndpointContext.getProcessData().getApikey();
        VideoJob videoJob = vs.queryVideoJob(id, apikey);
        if(videoJob == null) {
            throw new ResourceNotFoundException("video not found: " + id);
        }
        return videoJob;
    }

    @GetMapping("/{id}/content")
    public void retrieveVideoContent(
            @PathVariable("id") String videoId,
            @RequestParam(required = false) String variant,
            HttpServletResponse response) {
        Assert.hasText(videoId, "video_id is required");

        String apikey = EndpointContext.getProcessData().getApikey();
        VideoJob videoJob = vs.queryVideoJob(videoId, apikey);
        if(videoJob == null) {
            throw new ResourceNotFoundException("video not found: " + videoId);
        }

        Assert.isTrue(Status.completed.name().equals(videoJob.getStatus()),
                "video status must be completed, current status: " + videoJob.getStatus());

        String fileId = videoJob.getBoundFileId();
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

    @DeleteMapping("/{id}")
    public VideoJob deleteVideo(@PathVariable("id") String id) {
        Assert.hasText(id, "video_id is required");

        String apikey = EndpointContext.getProcessData().getApikey();
        VideoJob videoJob = vs.cancelVideoJob(id, apikey);
        if(videoJob == null) {
            throw new ResourceNotFoundException("video not found: " + id);
        }

        return videoJob;
    }
}
