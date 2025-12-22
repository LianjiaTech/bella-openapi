package com.ke.bella.openapi.configuration;

import lombok.extern.slf4j.Slf4j;
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

    /**
     * 统一的任务执行器（用于@Async和异步安全检测）
     * 配置为主要的TaskExecutor Bean
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(50);       // 核心线程数
		executor.setMaxPoolSize(150);       // 最大线程数
		executor.setQueueCapacity(2000);    // 队列容量
		executor.setKeepAliveSeconds(120);  // 空闲线程存活时间
		executor.setThreadNamePrefix("task-executor-");

		// 使用DiscardOldestPolicy：异步检测场景下，丢弃最旧任务比阻塞主线程更合适
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());

        // 异常处理装饰器
        executor.setTaskDecorator(runnable -> () -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.warn("异步任务执行失败", e);
            }
        });

        executor.initialize();
        return executor;
    }
}
