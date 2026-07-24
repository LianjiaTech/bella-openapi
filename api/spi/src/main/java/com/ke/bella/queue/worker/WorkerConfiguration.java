package com.ke.bella.queue.worker;

import com.theokanning.openai.service.OpenAiService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkerConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "bella.queue.worker", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean(Worker.class)
    @ConditionalOnBean(TaskExecutor.class)
    public Worker worker(TaskExecutor taskExecutor, OpenAiService openAiService) {
        return new Worker(taskExecutor, openAiService);
    }

    @Bean
    @ConfigurationProperties(value = "bella.queue.worker.scheduled")
    public ScheduledWorker.Properties sceduledWorkerProperties() {
        return new ScheduledWorker.Properties();
    }

    @Bean
    @ConditionalOnProperty(prefix = "bella.queue.worker.scheduled", name = "enabled", havingValue = "true")
    @ConditionalOnBean(Worker.class)
    public ScheduledWorker scheduledWorker(Worker worker) {
        ScheduledWorker scheduledWorker = new ScheduledWorker(sceduledWorkerProperties(), worker);
        scheduledWorker.start();
        Runtime.getRuntime().addShutdownHook(new Thread(scheduledWorker::stop));
        return scheduledWorker;
    }

}
