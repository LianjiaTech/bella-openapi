package com.ke.bella.openapi.optimization;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.RequestMetrics;
import com.ke.bella.openapi.configuration.RequestOptimizationConfig;
import com.ke.bella.openapi.protocol.completion.CompletionRequest;
import com.ke.bella.openapi.protocol.embedding.EmbeddingRequest;
import com.ke.bella.openapi.protocol.tts.TtsRequest;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.ke.bella.openapi.utils.TokenCalculationUtils;
import com.knuddels.jtokkit.api.EncodingType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 分阶段日志处理器 - 异步提取请求指标并优化内存使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageLoggingProcessor {

    private final RequestOptimizationConfig config;
    private final RequestSizeChecker requestSizeChecker;

    private ThreadPoolExecutor asyncExecutor;
    private final AtomicInteger rejectedTaskCount = new AtomicInteger(0);
    private final AtomicInteger syncFallbackCount = new AtomicInteger(0);

    @PostConstruct
    public void init() {
        // 创建有界队列的线程池，防止无限排队
        int corePoolSize = config.getAsyncThreadPoolSize();
        int maximumPoolSize = corePoolSize * 2;
        long keepAliveTime = 60L;
        TimeUnit unit = TimeUnit.SECONDS;

        // 有界队列，防止内存占用过大
        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(config.getAsyncThreadPoolSize() * 2);

        // 自定义拒绝策略：降级为同步处理
        RejectedExecutionHandler rejectedHandler = (r, executor) -> {
            rejectedTaskCount.incrementAndGet();
            log.warn("Thread pool rejected task, falling back to sync processing. Rejected count: {}",
                    rejectedTaskCount.get());

            // 在当前线程同步执行（降级策略）
            if(r instanceof OptimizationTask) {
                OptimizationTask task = (OptimizationTask) r;
                syncFallbackCount.incrementAndGet();
                log.debug("Executing optimization task synchronously. Sync fallback count: {}",
                        syncFallbackCount.get());
                task.runSynchronously();
            }
        };

        asyncExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                keepAliveTime,
                unit,
                workQueue,
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "request-optimization-" + threadNumber.getAndIncrement());
                        t.setDaemon(true);
                        return t;
                    }
                },
                rejectedHandler);

        log.info("StageLoggingProcessor initialized - CorePool: {}, MaxPool: {}, Queue: {}",
                corePoolSize, maximumPoolSize, workQueue.remainingCapacity());
    }

    @PreDestroy
    public void destroy() {
        if(asyncExecutor != null && !asyncExecutor.isShutdown()) {
            asyncExecutor.shutdown();
            log.info("StageLoggingProcessor executor shutdown");
        }
    }

    /**
     * 异步预处理请求数据
     */
    public void preprocessRequestAsync(EndpointProcessData processData, Object request) {
        if(!config.isEnabled()) {
            return;
        }

        OptimizationTask task = new OptimizationTask(processData, request);
        try {
            asyncExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            // 线程池拒绝时，拒绝策略会自动降级为同步处理
            log.debug("Task rejected by executor, handled by rejection policy");
        }
    }

    /**
     * 优化任务封装类
     */
    private class OptimizationTask implements Runnable {
        private final EndpointProcessData processData;
        private final Object request;

        public OptimizationTask(EndpointProcessData processData, Object request) {
            this.processData = processData;
            this.request = request;
        }

        @Override
        public void run() {
            try {
                preprocessRequest(processData, request);
            } catch (Exception e) {
                log.warn("Request preprocessing failed for endpoint: {}, requestId: {}",
                        processData.getEndpoint(), processData.getRequestId(), e);
            }
        }

        /**
         * 同步执行任务（降级策略使用）
         */
        public void runSynchronously() {
            run();
        }
    }

    /**
     * 预处理请求数据
     */
    private void preprocessRequest(EndpointProcessData processData, Object request) {
        long startTime = System.currentTimeMillis();

        try {
            String endpoint = processData.getEndpoint();
            if(endpoint == null) {
                log.debug("Endpoint is null, skipping preprocessing");
                return;
            }

            // 1. 提取请求指标
            RequestMetrics metrics = extractRequestMetrics(request, endpoint, processData);
            processData.setRequestMetrics(metrics);

            // 2. 生成请求摘要
            String summary = generateRequestSummary(request);
            processData.setRequestSummary(summary);

            // 3. 标记request已优化并清理原始引用
            processData.markRequestOptimized();

            long processingTime = System.currentTimeMillis() - startTime;
            log.debug("Request preprocessing completed for endpoint: {}, processing time: {}ms, " +
                    "optimized to metrics + summary",
                    endpoint, processingTime);

        } catch (Exception e) {
            log.error("Error during request preprocessing", e);
        }
    }

    /**
     * 提取请求指标 - 只提取LogHandler需要的核心信息
     */
    private RequestMetrics extractRequestMetrics(Object request, String endpoint, EndpointProcessData processData) {
        RequestMetrics.RequestMetricsBuilder builder = RequestMetrics.builder()
                .encodingType(processData.getEncodingType());

        // 根据不同endpoint提取特定指标
        switch (endpoint) {
        case "/v1/audio/speech":
            extractTtsMetrics(request, builder);
            break;

        case "/v1/chat/completions":
            extractCompletionMetrics(request, builder, processData.getEncodingType());
            break;

        case "/v1/embeddings":
            extractEmbeddingMetrics(request, builder, processData.getEncodingType());
            break;

        default:
            log.debug("No specific metrics extraction for endpoint: {}", endpoint);
        }

        return builder.build();
    }

    /**
     * 提取TTS请求指标 - 只提取inputLength
     */
    private void extractTtsMetrics(Object request, RequestMetrics.RequestMetricsBuilder builder) {
        if(request instanceof TtsRequest) {
            TtsRequest tts = (TtsRequest) request;
            builder.inputLength(tts.getInput() != null ? tts.getInput().length() : 0);
        }
    }

    /**
     * 提取Completion请求指标 - 只预计算inputTokens
     */
    private void extractCompletionMetrics(Object request, RequestMetrics.RequestMetricsBuilder builder, String encodingType) {
        if(request instanceof CompletionRequest) {
            CompletionRequest completion = (CompletionRequest) request;

            // 预计算输入token数量
            try {
                EncodingType encoding = EncodingType.fromName(encodingType).orElse(EncodingType.CL100K_BASE);
                int inputTokens = TokenCalculationUtils.calculateCompletionInputTokens(completion, encoding);
                builder.inputTokens(inputTokens);
            } catch (Exception e) {
                log.warn("Failed to calculate input tokens", e);
            }
        }
    }

    /**
     * 提取Embedding请求指标 - 只预计算embeddingTokens
     */
    private void extractEmbeddingMetrics(Object request, RequestMetrics.RequestMetricsBuilder builder, String encodingType) {
        if(request instanceof EmbeddingRequest) {
            EmbeddingRequest embedding = (EmbeddingRequest) request;
            EncodingType encoding = EncodingType.fromName(encodingType).orElse(EncodingType.CL100K_BASE);

            try {
                int tokens = TokenCalculationUtils.calculateEmbeddingTokens(embedding, encoding);
                builder.embeddingTokens(tokens);
            } catch (Exception e) {
                log.warn("Failed to calculate embedding tokens", e);
            }
        }
    }

    /**
     * 生成请求摘要
     */
    private String generateRequestSummary(Object request) {
        try {
            String fullContent = JacksonUtils.serialize(request);
            int maxLength = config.getRequestSummaryMaxLength();

            if(fullContent.length() <= maxLength) {
                return fullContent;
            }

            // 截取前部分并添加省略标记
            return fullContent.substring(0, maxLength) + "...[TRUNCATED]";

        } catch (Exception e) {
            log.warn("Failed to generate request summary", e);
            return "[SUMMARY_GENERATION_FAILED]";
        }
    }

}
