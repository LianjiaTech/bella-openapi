package com.ke.bella.openapi.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.TaskExecutor;
import com.ke.bella.openapi.apikey.ApikeyInfo;
import com.ke.bella.openapi.db.repo.VideoRepo;
import com.ke.bella.openapi.protocol.AdaptorManager;
import com.ke.bella.openapi.protocol.limiter.ChannelRpmLimiter;
import com.ke.bella.openapi.protocol.limiter.ChannelRpmLimiter.RpmStatus;
import com.ke.bella.openapi.protocol.video.VideoAdaptor;
import com.ke.bella.openapi.protocol.video.VideoJob;
import com.ke.bella.openapi.protocol.video.VideoJob.Status;
import com.ke.bella.openapi.protocol.video.VideoProperty;
import com.ke.bella.openapi.queue.VideoJobQueues;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.ke.bella.openapi.server.OpenapiProperties;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.service.ChannelService;
import com.ke.bella.openapi.service.ModelService;
import com.ke.bella.openapi.service.VideoService;
import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.openapi.tables.pojos.VideoJobDB;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.theokanning.openai.service.OpenAiService;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class VideoJobExecutor {

    @Resource
    private VideoJobQueues queueManager;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ChannelService channelService;

    @Resource
    private ModelService modelService;

    @Resource
    private ChannelRpmLimiter channelRpmLimiter;

    @Resource
    private VideoRepo videoRepo;

    @Resource
    private AdaptorManager adaptorManager;

    @Resource
    private OpenapiProperties openapiProperties;

    @Resource
    private VideoService videoService;

    @Resource
    private OpenAiServiceFactory openAiServiceFactory;

    @Resource
    private ApikeyService apikeyService;

    private OpenAiService videoFileService;

    private static final String MODEL_LOCK_PREFIX = "bella:video:model-lock:";

    private static final int RPM_SEGMENT_SIZE = 10;
    private static final int VIDEO_FILE_CONNECT_TIMEOUT = 60;
    private static final int VIDEO_FILE_READ_TIMEOUT = 600;

    @Value("${bella.video.schedule.interval:5}")
    private int scheduleIntervalSeconds;

    @Value("${bella.video.schedule.lock-timeout:20}")
    private int modelLockTimeoutSeconds;

    @Value("${bella.video.schedule.max-batch-size:50}")
    private int maxBatchSize;

    @Value("${bella.video.schedule.batch-size-ratio:0.2}")
    private double batchSizeRatio;

    @Value("${bella.video.schedule.sync-interval:5}")
    private int syncIntervalSeconds;

    @Value("${bella.video.schedule.sync-batch-size:20}")
    private int syncBatchSize;

    @Value("${bella.video.schedule.min-processing-seconds:30}")
    private int minProcessingSeconds;

    @PostConstruct
    public void start() {
        validateConfig();

        videoFileService = openAiServiceFactory.create(
                openapiProperties.getServiceAk(),
                VIDEO_FILE_CONNECT_TIMEOUT,
                VIDEO_FILE_READ_TIMEOUT);
        log.info("[VideoJob] Created video file service with timeout: connect={}s, read={}s",
                VIDEO_FILE_CONNECT_TIMEOUT, VIDEO_FILE_READ_TIMEOUT);

        TaskExecutor.scheduleAtFixedRate(() -> {
            try {
                processVideoJobs();
            } catch (Exception e) {
                log.error("[VideoJob] Failed to process video jobs", e);
            }
        }, scheduleIntervalSeconds);

        log.info("[VideoJob] Video job executor started with config: interval={}s, lockTimeout={}s, " +
                "maxBatch={}, batchRatio={}",
                scheduleIntervalSeconds, modelLockTimeoutSeconds,
                maxBatchSize, batchSizeRatio);
    }

    @PostConstruct
    public void startSyncScheduler() {
        TaskExecutor.scheduleAtFixedRate(() -> {
            try {
                processSyncQueue();
            } catch (Exception e) {
                log.error("Failed to process sync queue", e);
            }
        }, syncIntervalSeconds);

        log.info("Video job sync scheduler started with config: interval={}s, batchSize={}, minProcessingSeconds={}s",
                syncIntervalSeconds, syncBatchSize, minProcessingSeconds);
    }

    private void validateConfig() {
        if(modelLockTimeoutSeconds <= scheduleIntervalSeconds) {
            log.warn("[VideoJob] Config warning: Lock timeout ({}s) should be greater than schedule interval ({}s) " +
                    "to prevent lock expiration during processing",
                    modelLockTimeoutSeconds, scheduleIntervalSeconds);
        }

        if(RPM_SEGMENT_SIZE % scheduleIntervalSeconds != 0 &&
                scheduleIntervalSeconds % RPM_SEGMENT_SIZE != 0) {
            log.warn("[VideoJob] Config warning: Schedule interval ({}s) should align with RPM segment size ({}s) " +
                    "for optimal rate limiting",
                    scheduleIntervalSeconds, RPM_SEGMENT_SIZE);
        }

        int schedulesPerSegment = Math.max(1, RPM_SEGMENT_SIZE / scheduleIntervalSeconds);
        double maxSafeRatio = 1.0 / schedulesPerSegment;
        if(batchSizeRatio > maxSafeRatio) {
            log.warn("[VideoJob] Config warning: Batch size ratio ({}) may cause RPM exhaustion. " +
                    "With interval={}s and RPM segment={}s, suggested max ratio: {}",
                    batchSizeRatio, scheduleIntervalSeconds, RPM_SEGMENT_SIZE, maxSafeRatio);
        }
    }

    private void processVideoJobs() {
        List<String> videoModels = modelService.listModelNamesByEndpoint(VideoJob.ENDPOINT);

        if(videoModels.isEmpty()) {
            log.debug("[VideoJob] No video models found");
            return;
        }

        log.debug("[VideoJob] Processing {} video models: {}", videoModels.size(), videoModels);

        for (String model : videoModels) {
            try {
                processSubmitQueue(model);
            } catch (Exception e) {
                log.error("[VideoJob] Failed to process submit queue: {}", model, e);
            }
        }
    }

    private void processSubmitQueue(String model) {
        RLock lock = redissonClient.getLock(MODEL_LOCK_PREFIX + model);
        boolean locked = false;
        long startTime = System.currentTimeMillis();

        try {
            locked = lock.tryLock(0, modelLockTimeoutSeconds, TimeUnit.SECONDS);
            if(!locked) {
                log.debug("[VideoJob] Model {} queue is locked by another instance, skip", model);
                return;
            }

            List<ChannelDB> channels = channelService.listActives("model", model);
            if(channels.isEmpty()) {
                log.warn("[VideoJob] No active channels configured for model: {}", model);
                return;
            }

            log.info("[VideoJob] Model: {}, total channels: {}", model, channels.size());

            List<ChannelDB> availableChannels = selectChannelsWithAvailableRpm(channels);
            if(availableChannels.isEmpty()) {
                log.warn("[VideoJob] Model: {}, all {} channels have exhausted RPM quota", model, channels.size());
                return;
            }

            log.info("[VideoJob] Model: {}, available channels: {} (filtered from {})",
                    model, availableChannels.size(), channels.size());

            int batchSize = calculateSafeBatchSizeByRpm(availableChannels);
            if(batchSize <= 0) {
                log.warn("[VideoJob] Model: {}, calculated batch size is 0 due to RPM constraints", model);
                return;
            }

            log.info("[VideoJob] Model: {}, calculated batch size: {}", model, batchSize);

            List<String> videoIds = queueManager.dequeueForSubmit(model, batchSize);
            if(videoIds.isEmpty()) {
                log.info("[VideoJob] Model: {}, no pending tasks in queue", model);
                return;
            }

            log.info("[VideoJob] Model: {}, dequeued {} tasks from queue, will assign to {} channels",
                    model, videoIds.size(), availableChannels.size());

            submitBatchTasksToChannels(videoIds, availableChannels);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[VideoJob] Thread interrupted while processing model: {}", model);
        } catch (Exception e) {
            log.error("[VideoJob] Unexpected error processing model queue: {}", model, e);
        } finally {
            if(locked) {
                try {
                    lock.unlock();
                } catch (IllegalMonitorStateException e) {
                    long duration = System.currentTimeMillis() - startTime;
                    log.warn("[VideoJob] Distributed lock expired for model: {} (processing took {}ms, timeout={}s). " +
                            "Consider increasing lock timeout.",
                            model, duration, modelLockTimeoutSeconds * 1000);
                } catch (Exception e) {
                    log.error("[VideoJob] Failed to release distributed lock for model: {}", model, e);
                }
            }
        }
    }

    private List<ChannelDB> selectChannelsWithAvailableRpm(List<ChannelDB> channels) {
        List<ChannelDB> available = new ArrayList<>();

        for (ChannelDB channel : channels) {
            Integer rpm = parseRpmFromChannel(channel);

            if(rpm == null) {
                available.add(channel);
                continue;
            }

            RpmStatus status = channelRpmLimiter.getRpmStatus(
                    channel.getChannelCode(),
                    rpm);

            if(status.isAvailable()) {
                available.add(channel);
                log.debug("[VideoJob] Channel {} RPM status: {}", channel.getChannelCode(), status);
            } else {
                log.info("[VideoJob] Channel {} filtered out due to RPM exhausted: {}",
                        channel.getChannelCode(), status);
            }
        }

        return available;
    }

    private int calculateSafeBatchSizeByRpm(List<ChannelDB> channels) {
        int totalRemaining = 0;
        boolean hasUnlimited = false;

        for (ChannelDB channel : channels) {
            Integer rpm = parseRpmFromChannel(channel);

            if(rpm == null) {
                hasUnlimited = true;
                break;
            }

            RpmStatus status = channelRpmLimiter.getRpmStatus(
                    channel.getChannelCode(),
                    rpm);

            totalRemaining += status.getRemaining();
        }

        if(hasUnlimited) {
            log.debug("[VideoJob] At least one channel has unlimited RPM, using max batch size: {}", maxBatchSize);
            return maxBatchSize;
        }

        int safeBatchSize = (int) (totalRemaining * batchSizeRatio);

        if(safeBatchSize == 0 && totalRemaining > 0) {
            safeBatchSize = Math.min(1, totalRemaining);
            log.info("[VideoJob] Batch size was 0, adjusted to minimum: 1 (totalRemaining={})", totalRemaining);
        }

        int finalBatchSize = Math.min(safeBatchSize, maxBatchSize);

        log.info("[VideoJob] Batch size calculation: totalRemainingRpm={}, ratio={}, calculatedSafe={}, configuredMax={}, final={}",
                totalRemaining, batchSizeRatio, safeBatchSize, maxBatchSize, finalBatchSize);

        return finalBatchSize;
    }

    private void submitBatchTasksToChannels(List<String> videoIds, List<ChannelDB> channels) {
        AssignmentMetrics metrics = new AssignmentMetrics();
        int channelIndex = 0;

        for (String videoId : videoIds) {
            SubmissionResult result = submitSingleTask(videoId, channels, channelIndex++);
            updateMetrics(metrics, result);
        }

        logBatchResult(videoIds.size(), metrics);
    }

    private SubmissionResult submitSingleTask(String videoId, List<ChannelDB> channels, int channelIndex) {
        VideoJobDB job = loadTaskForSubmission(videoId);
        if(job == null) {
            return SubmissionResult.SKIPPED;
        }

        ChannelDB channel = selectChannelByRoundRobin(channels, channelIndex);

        if(!consumeChannelRpmQuota(channel, videoId)) {
            return returnTaskToQueue(videoId) ? SubmissionResult.RE_ENQUEUED : SubmissionResult.SKIPPED;
        }

        return claimTaskAndSubmitToWorker(videoId, channel) ? SubmissionResult.SUBMITTED : SubmissionResult.SKIPPED;
    }

    private VideoJobDB loadTaskForSubmission(String videoId) {
        VideoJobDB job = videoRepo.queryVideoJob(videoId);
        if(!isTaskInQueuedStatus(job, videoId)) {
            return null;
        }
        return job;
    }

    private void updateMetrics(AssignmentMetrics metrics, SubmissionResult result) {
        switch (result) {
        case SUBMITTED:
            metrics.submitted++;
            break;
        case RE_ENQUEUED:
            metrics.reEnqueued++;
            break;
        case SKIPPED:
            metrics.skipped++;
            break;
        }
    }

    private void logBatchResult(int totalTasks, AssignmentMetrics metrics) {
        log.info("[VideoJob] Batch assignment completed: total={}, submitted={}, reEnqueued={}, skipped={}",
                totalTasks, metrics.submitted, metrics.reEnqueued, metrics.skipped);

        if(metrics.submitted == 0 && totalTasks > 0) {
            log.warn("[VideoJob] No tasks submitted out of {} tasks", totalTasks);
        }
    }

    private enum SubmissionResult {
        SUBMITTED, RE_ENQUEUED, SKIPPED
    }

    private boolean isTaskInQueuedStatus(VideoJobDB job, String videoId) {
        if(job == null || !Status.queued.name().equals(job.getStatus())) {
            log.debug("[VideoJob {}] Not ready for submit: status={}",
                    videoId, job != null ? job.getStatus() : "null");
            return false;
        }
        return true;
    }

    private ChannelDB selectChannelByRoundRobin(List<ChannelDB> channels, int index) {
        return channels.get(index % channels.size());
    }

    private boolean consumeChannelRpmQuota(ChannelDB channel, String videoId) {
        Integer rpm = parseRpmFromChannel(channel);
        if(rpm == null) {
            return true;
        }

        boolean consumed = channelRpmLimiter.consumeRpm(channel.getChannelCode(), rpm);
        if(!consumed) {
            log.warn("[VideoJob {}] Channel RPM exhausted, will re-enqueue: channelCode={}",
                    videoId, channel.getChannelCode());
        }
        return consumed;
    }

    private boolean claimTaskAndSubmitToWorker(String videoId, ChannelDB channel) {
        boolean claimed = videoRepo.casUpdateToSubmitting(
                videoId,
                channel.getChannelCode());

        if(claimed) {
            TaskExecutor.submit(
                    VideoJobSubmitTask.builder()
                            .videoId(videoId)
                            .channel(channel)
                            .videoRepo(videoRepo)
                            .adaptorManager(adaptorManager)
                            .queueManager(queueManager)
                            .build());
            log.debug("[VideoJob {}] Claimed and submitted to worker: channelCode={}",
                    videoId, channel.getChannelCode());
            return true;
        } else {
            log.warn("[VideoJob {}] Failed to claim (CAS conflict or concurrent modification)", videoId);
            return false;
        }
    }

    private static class AssignmentMetrics {
        int submitted = 0;
        int reEnqueued = 0;
        int skipped = 0;
    }

    private boolean returnTaskToQueue(String videoId) {
        try {
            VideoJobDB job = videoRepo.queryVideoJob(videoId);
            if(job == null) {
                log.error("[VideoJob {}] Cannot re-enqueue: job not found", videoId);
                return false;
            }

            queueManager.enqueueForSubmitFirst(job.getModel(), videoId);
            log.info("[VideoJob {}] Re-enqueued to HEAD (priority retry due to RPM exhausted): model={}", videoId, job.getModel());
            return true;
        } catch (Exception e) {
            log.error("[VideoJob {}] Failed to re-enqueue", videoId, e);
            return false;
        }
    }

    private Integer parseRpmFromChannel(ChannelDB channel) {
        try {
            VideoAdaptor<?> adaptor = (VideoAdaptor<?>) adaptorManager.getProtocolAdaptor(
                    VideoJob.ENDPOINT,
                    channel.getProtocol());

            if(adaptor == null) {
                return null;
            }

            Class<?> propertyClass = adaptor.getPropertyClass();
            VideoProperty property = (VideoProperty) JacksonUtils.deserialize(
                    channel.getChannelInfo(),
                    propertyClass);
            return property != null ? property.getRpm() : null;
        } catch (Exception e) {
            log.warn("[VideoJob] Failed to parse channel property for RPM: channelCode={}",
                    channel.getChannelCode(), e);
            return null;
        }
    }

    private void processSyncQueue() {
        List<String> videoIds = queueManager.dequeueForSync(syncBatchSize);

        if(videoIds.isEmpty()) {
            log.debug("[VideoJob] No tasks in sync queue");
            return;
        }

        log.info("[VideoJob] Dequeued {} tasks for syncing", videoIds.size());

        for (String videoId : videoIds) {
            try {
                VideoJobDB job = videoRepo.queryVideoJob(videoId);
                if(job == null) {
                    log.warn("[VideoJob {}] Job not found when preparing sync, skip", videoId);
                    continue;
                }

                ChannelDB channel = channelService.getOne(job.getChannelCode());
                ApikeyInfo apikeyInfo = apikeyService.queryByCode(job.getAkCode(), false);

                EndpointProcessData processData = buildProcessData(job, channel, apikeyInfo);

                TaskExecutor.submit(
                        VideoJobSyncTask.builder()
                                .videoId(videoId)
                                .videoRepo(videoRepo)
                                .queueManager(queueManager)
                                .adaptorManager(adaptorManager)
                                .channel(channel)
                                .processData(processData)
                                .videoService(videoService)
                                .videoFileService(videoFileService)
                                .minProcessingSeconds(minProcessingSeconds)
                                .build());
            } catch (Exception e) {
                log.error("[VideoJob {}] Failed to prepare sync task context", videoId, e);
            }
        }
    }

    private EndpointProcessData buildProcessData(VideoJobDB job, ChannelDB channel, ApikeyInfo apikeyInfo) {
        EndpointProcessData processData = new EndpointProcessData();
        processData.setRequestId(job.getVideoId());
        processData.setEndpoint(VideoJob.ENDPOINT);
        processData.setModel(job.getModel());
        processData.setAkCode(job.getAkCode());
        processData.setChannelCode(job.getChannelCode());
        processData.setProtocol(channel.getProtocol());
        processData.setSupplier(channel.getSupplier());
        processData.setPriceInfo(channel.getPriceInfo());
        processData.setAccountCode(apikeyInfo.getOwnerCode());
        processData.setAccountType(apikeyInfo.getOwnerType());
        processData.setParentAkCode(apikeyInfo.getParentCode());

        return processData;
    }
}
