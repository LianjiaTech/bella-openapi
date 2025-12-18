package com.ke.bella.openapi.endpoints;

import javax.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ke.bella.openapi.annotations.EndpointAPI;
import com.ke.bella.openapi.protocol.OpenapiListResponse;
import com.ke.bella.openapi.protocol.video.VideoCreateRequest;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoRemixRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@EndpointAPI
@RestController
@RequestMapping("/v1/videos")
@Tag(name = "videos")
@Slf4j
public class VideoController {

    @PostMapping
    public VideoJob createVideo(@RequestBody VideoCreateRequest request) {
        return null;
    }

    @GetMapping("/{id}")
    public VideoJob retrieveVideo(@PathVariable("id") String id) {
        return null;
    }

    @GetMapping("/{id}/content")
    public void retrieveVideoContent(
            @PathVariable("id") String id,
            @RequestParam(required = false) String variant,
            HttpServletResponse response) {
    }

    @PostMapping("/{id}/remix")
    public VideoJob remixVideo(@PathVariable("id") String id, @RequestBody VideoRemixRequest request) {
        return null;
    }

    @GetMapping
    public OpenapiListResponse<VideoJob> listVideos(
            @RequestParam(required = false) String after,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String order) {
        return null;
    }

    @DeleteMapping("/{id}")
    public VideoJob deleteVideo(@PathVariable("id") String id) {
        return null;
    }
}
