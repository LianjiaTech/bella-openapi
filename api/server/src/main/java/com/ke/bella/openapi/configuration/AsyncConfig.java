package com.ke.bella.openapi.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 *
 * @author claude
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {
    @Value("${bella.endpoint.async.core-pool-size:${bella.chat.async.core-pool-size:200}}")
    private int endpointAsyncCorePoolSize;
    @Value("${bella.endpoint.async.max-pool-size:${bella.chat.async.max-pool-size:500}}")
    private int endpointAsyncMaxPoolSize;
    @Value("${bella.endpoint.async.queue-capacity:${bella.chat.async.queue-capacity:1000}}")
    private int endpointAsyncQueueCapacity;

    /**
     * 缓存清理任务线程池
     */
    @Bean("cacheTaskExecutor")
    public Executor cacheTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("cache-clear-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 设置任务装饰器，添加异常处理
        executor.setTaskDecorator(runnable -> () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.warn("异步缓存清理任务执行失败", e);
            }
        });

        executor.initialize();
        return executor;
    }

    @Bean({ "endpointAsyncExecutor", "chatCompletionExecutor" })
    public ThreadPoolTaskExecutor endpointAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(endpointAsyncCorePoolSize);
        executor.setMaxPoolSize(endpointAsyncMaxPoolSize);
        executor.setQueueCapacity(endpointAsyncQueueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("endpoint-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

}
