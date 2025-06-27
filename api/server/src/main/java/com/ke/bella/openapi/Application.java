package com.ke.bella.openapi;

import com.ke.bella.job.queue.config.EnableJobQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.alicp.jetcache.anno.config.EnableMethodCache;

@ComponentScan(basePackages = { "com.ke.bella.openapi" })
@SpringBootApplication
@EnableMethodCache(basePackages = "com.ke.bella.openapi")
@EnableScheduling
@EnableJobQueue
public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        try {
            LOGGER.info("Starting application...");
            SpringApplication.run(Application.class, args);
            LOGGER.info("Application started successfully!");
        } catch (Exception e) {
            LOGGER.error("Failed to start application", e);
            throw e;
        }
    }
}
