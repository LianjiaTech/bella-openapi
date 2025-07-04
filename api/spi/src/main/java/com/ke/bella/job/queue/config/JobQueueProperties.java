package com.ke.bella.job.queue.config;

import lombok.Data;

@Data
public class JobQueueProperties {
   private String url;
   private Integer defaultTimeout = 300;
   
   // Worker配置
   private WorkerConfig worker = new WorkerConfig();
   
   @Data
   public static class WorkerConfig {
       private boolean enabled = false;           // 是否启用worker
       private int pollInterval = 5000;          // 轮询间隔(毫秒)
       private int pollSize = 10;                // 每次拉取任务数
       private int retryQueueSize = 1000;        // 重试队列大小
       private int threadPoolSize = 2;           // 线程池大小
   }
}
