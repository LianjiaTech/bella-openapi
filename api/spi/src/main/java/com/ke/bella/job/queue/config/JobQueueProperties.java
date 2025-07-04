package com.ke.bella.job.queue.config;

import lombok.Data;

@Data
public class JobQueueProperties {
   private String url;
   private Integer defaultTimeout = 300;
   
   /**
    * Worker相关配置
    */
   private WorkerConfig worker = new WorkerConfig();
   
   @Data
   public static class WorkerConfig {
       /**
        * 是否启用worker
        */
       private boolean enabled = false;
       
       /**
        * 轮询间隔(毫秒)
        */
       private long pollInterval = 5000;
       
       /**
        * 每次轮询的任务数量
        */
       private int pollSize = 1;
       
       /**
        * 重试队列大小
        */
       private int retryQueueSize = 1000;
       
       /**
        * 工作线程池大小
        */
       private int threadPoolSize = 2;
   }
}
