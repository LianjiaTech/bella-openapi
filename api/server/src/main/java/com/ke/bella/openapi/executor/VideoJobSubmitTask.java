package com.ke.bella.openapi.executor;

import com.ke.bella.openapi.db.repo.VideoRepo;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.video.VideoAdaptor;
import com.ke.bella.openapi.protocol.video.VideoCreateRequest;
import com.ke.bella.openapi.executor.VideoJobException.Code;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import com.ke.bella.openapi.protocol.video.VideoProperty;
import com.ke.bella.openapi.queue.VideoJobQueues;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.VideoJobDB;
import com.ke.bella.openapi.utils.JacksonUtils;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class VideoJobSubmitTask implements Runnable {

    private final String videoId;
    private final ChannelDB channel;
    private final VideoRepo videoRepo;
    private final AdaptorManager adaptorManager;
    private final VideoJobQueues queueManager;

    @Override
    public void run() {
        try {
            VideoJobDB job = loadJob();
            String channelVideoId = submitVideoTask(job, channel);
            updateToProcessing(channelVideoId);

        } catch (VideoJobException e) {
            log.error("[VideoJob {}] Business error during submit: errorCode={}, errorMessage={}",
                videoId, e.getCode(), e.getMessage());
            markAsFailed(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[VideoJob {}] Unexpected exception during submission task", videoId, e);
            markAsFailed("submit_exception",
                "Exception during video task submission: " + e.getMessage());
        }
    }

    private VideoJobDB loadJob() {
        VideoJobDB job = videoRepo.queryVideoJob(videoId);
        if (job == null) {
            throw new VideoJobException(Code.JOB_NOT_FOUND, "Video job not found in database");
        }

        if (!Status.submitting.name().equals(job.getStatus())) {
            log.warn("[VideoJob {}] Job status is not 'submitting', skip execution: currentStatus={}",
                videoId, job.getStatus());
            throw new VideoJobException(Code.INVALID_STATUS,
                "Job status is not 'submitting': " + job.getStatus());
        }

        return job;
    }

    private void updateToProcessing(String channelVideoId) {
        boolean updated = videoRepo.casUpdateToProcessing(videoId, channelVideoId);

        if (updated) {
            queueManager.enqueueForSync(videoId);
            log.info("[VideoJob {}] Status changed: queued -> processing, channelVideoId={}, enqueued for syncing",
                videoId, channelVideoId);
        } else {
            log.error("[VideoJob {}] CAS update to 'processing' failed (status changed or concurrent modification), " +
                    "but channel already has the task. Marking as failed to prevent orphaned channel task.", videoId);
            throw new VideoJobException(Code.STATE_UPDATE_FAILED,
                "Failed to update job status to processing after channel submission, possible concurrent modification");
        }
    }

    private void markAsFailed(String errorCode, String errorMessage) {
        try {
            VideoJobDB failedJob = new VideoJobDB();
            failedJob.setVideoId(videoId);
            failedJob.setStatus(Status.failed.name());

            java.util.Map<String, String> errorMap = new java.util.HashMap<>();
            errorMap.put("code", errorCode);
            errorMap.put("message", errorMessage);
            failedJob.setError(JacksonUtils.serialize(errorMap));

            videoRepo.batchUpdateVideoJobs(java.util.Collections.singletonList(failedJob));
            log.info("[VideoJob {}] Marked as failed: errorCode={}, errorMessage={}", videoId, errorCode, errorMessage);
        } catch (Exception ex) {
            log.error("[VideoJob {}] Critical: Failed to update status to 'failed' in database: errorCode={}",
                    videoId, errorCode, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private String submitVideoTask(VideoJobDB job, ChannelDB channel) {
        VideoAdaptor<?> adaptor = (VideoAdaptor<?>) adaptorManager.getProtocolAdaptor(
            VideoJob.ENDPOINT,
            channel.getProtocol()
        );

        if (adaptor == null) {
            throw new VideoJobException(Code.ADAPTOR_NOT_FOUND,
                "No video adaptor found for protocol: " + channel.getProtocol());
        }

        VideoCreateRequest request = VideoCreateRequest.builder()
            .prompt(job.getPrompt())
            .model(job.getModel())
            .seconds(job.getSeconds() != null ? String.valueOf(job.getSeconds()) : null)
            .size(job.getSize())
            .input_reference(job.getInputReferenceFileId())
            .build();

        Class<?> propertyClass = adaptor.getPropertyClass();
        Object property = JacksonUtils.deserialize(
            channel.getChannelInfo(),
            propertyClass
        );

        String baseUrl = channel.getUrl();

        return ((VideoAdaptor<VideoProperty>) adaptor).submitVideoTask(
            request,
            baseUrl,
            (VideoProperty) property,
            job.getVideoId()
        );
    }
}
