package com.ke.bella.openapi.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.db.VideoIdGenerator;
import com.ke.bella.openapi.db.repo.VideoRepo;
import com.ke.bella.openapi.protocol.log.EndpointLogger;
import com.ke.bella.openapi.protocol.video.ChannelVideoResult;
import com.ke.bella.openapi.protocol.video.VideoCreateRequest;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import com.ke.bella.openapi.queue.VideoJobQueues;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.VideoJobDB;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VideoService {

    @Resource
    private VideoRepo videoRepo;

    @Resource
    private VideoJobQueues queueManager;

    @Resource
    private EndpointLogger endpointLogger;

    @Resource
    private ChannelService channelService;

    @Transactional(rollbackFor = Exception.class)
    public VideoJobDB createVideoJob(
            VideoCreateRequest request,
            String spaceCode,
            String akCode) {
        String model = request.getModel();

        String videoId = VideoIdGenerator.VIDEO_ID_GENERATOR.generate(spaceCode);

        VideoJobDB videoJobDB = new VideoJobDB();
        videoJobDB.setVideoId(videoId);
        videoJobDB.setSpaceCode(spaceCode);
        videoJobDB.setAkCode(akCode);
        videoJobDB.setPrompt(request.getPrompt());
        videoJobDB.setModel(model);
        videoJobDB.setSize(request.getSize());
        if(request.getSeconds() != null) {
            videoJobDB.setSeconds(Long.parseLong(request.getSeconds()));
        }
        if(request.getInput_reference() != null) {
            videoJobDB.setInputReferenceFileId(request.getInput_reference());
        }
        videoJobDB.setStatus(Status.queued.name());

        videoRepo.addVideoJob(videoJobDB);

        queueManager.enqueueForSubmit(model, videoId);

        log.info("[VideoJob {}] Created with status 'queued' and enqueued to Redis: model={}", videoId, model);

        return videoJobDB;
    }

    public VideoJobDB queryVideoJob(String videoId) {
        return videoRepo.queryVideoJob(videoId);
    }

    public List<VideoJobDB> listVideoJobs(String spaceCode, String after, Integer limit, String order) {
        return videoRepo.listVideoJobs(spaceCode, after, limit, order);
    }

    @Transactional(rollbackFor = Exception.class)
    public VideoJobDB deleteVideoJob(String videoId) {
        VideoJobDB videoJobDB = videoRepo.queryVideoJob(videoId);
        if(videoJobDB == null) {
            return null;
        }

        String currentStatus = videoJobDB.getStatus();
        boolean canDelete = Status.queued.name().equals(currentStatus) ||
                Status.completed.name().equals(currentStatus) ||
                Status.failed.name().equals(currentStatus) ||
                Status.cancelled.name().equals(currentStatus);

        if(!canDelete) {
            throw new IllegalStateException(
                    "Cannot delete video job in status: " + currentStatus +
                            ". Only queued, completed, failed, or cancelled jobs can be deleted.");
        }

        videoRepo.updateVideoJobStatus(videoId, Status.deleted.name());
        videoJobDB.setStatus(Status.deleted.name());
        return videoJobDB;
    }

    @Transactional(rollbackFor = Exception.class)
    public void processSyncResult(String videoId, ChannelVideoResult result,
            ChannelDB channel, EndpointProcessData processData) {
        VideoJobDB job = videoRepo.queryVideoJob(videoId);
        if(job == null) {
            log.error("[VideoJob {}] Job not found when processing sync result", videoId);
            return;
        }

        if(!Status.processing.name().equals(job.getStatus())) {
            log.info("[VideoJob {}] Job is not in valid state for sync, skip sync result: currentStatus={}",
                    videoId, job.getStatus());
            return;
        }

        VideoJobDB update = new VideoJobDB();
        update.setVideoId(videoId);
        update.setStatus(result.getStatus());

        if(result.getFileId() != null) {
            update.setBoundFileId(result.getFileId());
        }

        if(result.getActualSeconds() != null) {
            long milliseconds = (long) (result.getActualSeconds() * 1000);
            update.setSeconds(milliseconds);
        }

        if(result.getSize() != null) {
            update.setSize(result.getSize());
        }

        if(Status.completed.name().equals(result.getStatus())) {
            update.setCompletedAt(LocalDateTime.now());
            update.setProgress(100);
        }

        if(result.getError() != null) {
            String errorJson = JacksonUtils.serialize(result.getError());
            update.setError(errorJson);
        }

        videoRepo.batchUpdateVideoJobs(Collections.singletonList(update));

        if(Status.completed.name().equals(result.getStatus()) && result.getUsage() != null) {
            logVideoCost(job, result, processData);
        }

        log.info("[VideoJob {}] Status updated: {} -> {}", videoId, job.getStatus(), result.getStatus());
    }

    private void logVideoCost(VideoJobDB job, ChannelVideoResult result, EndpointProcessData processData) {
        try {
            processData.setUsage(result.getUsage());
            processData.setInnerLog(true);
            processData.setRequestTime(
                    job.getCtime().atZone(ZoneId.systemDefault()).toEpochSecond());

            endpointLogger.log(processData);
            log.debug("[VideoJob {}] Cost logged with ownerCode: {}",
                    job.getVideoId(), processData.getAccountCode());
        } catch (Exception e) {
            log.error("[VideoJob {}] Failed to log video cost", job.getVideoId(), e);
        }
    }
}
