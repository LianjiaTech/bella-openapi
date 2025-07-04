package com.ke.bella.job.queue.config;

import lombok.Data;

@Data
public class JobQueueProperties {
   private String url;
   private Integer defaultTimeout = 300;
   private WorkerConfig worker = new WorkerConfig();
   
   @Data
   public static class WorkerConfig {
       private boolean enabled = false;
       private int pollInterval = 5000;
       private int pollSize = 10;
       private int retryQueueSize = 1000;
       private int threadPoolSize = 2;
   }
}
