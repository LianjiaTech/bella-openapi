package com.ke.bella.openapi.executor;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.db.repo.VideoRepo;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.video.ChannelVideoResult;
import com.ke.bella.openapi.protocol.video.VideoAdaptor;
import com.ke.bella.openapi.executor.VideoJobException.Code;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import com.ke.bella.openapi.protocol.video.VideoProperty;
import com.ke.bella.openapi.queue.VideoJobQueues;
import com.ke.bella.openapi.service.VideoService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.VideoJobDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.file.File;
import com.theokanning.openai.service.OpenAiService;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class VideoJobSyncTask implements Runnable {

    private final String videoId;
    private final VideoRepo videoRepo;
    private final VideoJobQueues queueManager;
    private final AdaptorManager adaptorManager;
    private final ChannelDB channel;
    private final EndpointProcessData processData;
    private final VideoService videoService;
    private final OpenAiService videoFileService;
    private final int minProcessingSeconds;

    @Override
    public void run() {
        try {
            VideoJobDB job = loadJob();

            if(!shouldSyncNow(job)) {
                log.debug("[VideoJob {}] Min processing time not reached, re-enqueue", videoId);
                queueManager.enqueueForSync(videoId);
                return;
            }

            ChannelVideoResult result = queryChannelVideoStatus(job, channel);

            if(isTerminalState(result.getStatus())) {
                handleTerminalState(result, channel);
            } else {
                queueManager.enqueueForSync(videoId);
                log.debug("[VideoJob {}] Still processing on channel, re-enqueued", videoId);
            }

        } catch (VideoJobException e) {
            log.error("[VideoJob {}] Business error during sync: errorCode={}, errorMessage={}",
                videoId, e.getCode(), e.getMessage());
            markAsFailed(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[VideoJob {}] Unexpected error during sync", videoId, e);
            markAsFailed("sync_exception", "Exception during video sync: " + e.getMessage());
        }
    }

    private VideoJobDB loadJob() {
        VideoJobDB job = videoRepo.queryVideoJob(videoId);
        if(job == null) {
            throw new VideoJobException(Code.JOB_NOT_FOUND, "Video job not found in database");
        }

        if(!Status.processing.name().equals(job.getStatus())) {
            log.debug("[VideoJob {}] No longer processing, skip syncing: status={}", videoId, job.getStatus());
            throw new VideoJobException(Code.INVALID_STATUS, "Job status is not 'processing': " + job.getStatus());
        }

        return job;
    }

    private boolean shouldSyncNow(VideoJobDB job) {
        long processingTime = System.currentTimeMillis() -
                Timestamp.valueOf(job.getMtime()).getTime();
        return processingTime >= minProcessingSeconds * 1000L;
    }

    private boolean isTerminalState(String status) {
        return Status.completed.name().equals(status) ||
                Status.failed.name().equals(status) ||
                Status.cancelled.name().equals(status);
    }

    private void handleTerminalState(ChannelVideoResult result, ChannelDB channel) {
        if(Status.completed.name().equals(result.getStatus())) {
            transferVideoToFile(result, channel);
        }

        videoService.processSyncResult(videoId, result, channel, processData);
    }

    private void markAsFailed(String errorCode, String errorMessage) {
        try {
            VideoJobDB failedJob = new VideoJobDB();
            failedJob.setVideoId(videoId);
            failedJob.setStatus(Status.failed.name());

            Map<String, String> errorMap = new HashMap<>();
            errorMap.put("code", errorCode);
            errorMap.put("message", errorMessage);
            failedJob.setError(JacksonUtils.serialize(errorMap));

            videoRepo.batchUpdateVideoJobs(Collections.singletonList(failedJob));
            log.info("[VideoJob {}] Marked as failed: errorCode={}, errorMessage={}", videoId, errorCode, errorMessage);
        } catch (Exception ex) {
            log.error("[VideoJob {}] Critical: Failed to update status to 'failed' in database: errorCode={}",
                    videoId, errorCode, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private ChannelVideoResult queryChannelVideoStatus(VideoJobDB job, ChannelDB channel) {
        VideoAdaptor<?> adaptor = (VideoAdaptor<?>) adaptorManager.getProtocolAdaptor(
                VideoJob.ENDPOINT,
                channel.getProtocol());

        if(adaptor == null) {
            throw new VideoJobException(Code.ADAPTOR_NOT_FOUND,
                "No video adaptor found for protocol: " + channel.getProtocol());
        }

        Class<?> propertyClass = adaptor.getPropertyClass();
        VideoProperty property = (VideoProperty) JacksonUtils.deserialize(
                channel.getChannelInfo(),
                propertyClass);

        String baseUrl = channel.getUrl();

        return ((VideoAdaptor<VideoProperty>) adaptor).queryVideoTask(
                job.getChannelVideoId(),
                baseUrl,
                property);
    }

    @SuppressWarnings("unchecked")
    private void transferVideoToFile(ChannelVideoResult result, ChannelDB channel) {
        VideoAdaptor<?> adaptor = (VideoAdaptor<?>) adaptorManager.getProtocolAdaptor(
                VideoJob.ENDPOINT,
                channel.getProtocol());

        if(adaptor == null) {
            throw new VideoJobException(Code.ADAPTOR_NOT_FOUND,
                "No video adaptor registered for protocol: " + channel.getProtocol());
        }

        Class<?> propertyClass = adaptor.getPropertyClass();
        VideoProperty property = (VideoProperty) JacksonUtils.deserialize(
                channel.getChannelInfo(),
                propertyClass);

        String baseUrl = channel.getUrl();

        File file = ((VideoAdaptor<VideoProperty>) adaptor).transferVideoToFile(
                result.getChannelVideoId(),
                baseUrl,
                property,
                videoFileService);

        if(file == null || file.getId() == null) {
            throw new VideoJobException(Code.TRANSFER_FAILED,
                "Transfer returned null or empty fileId");
        }

        result.setFileId(file.getId());
    }
}
