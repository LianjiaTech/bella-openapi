package com.ke.bella.queue;

import lombok.Data;

import java.util.List;

@Data
public class WorkerConfig {
    private String serviceUrl;
    private String consoleKey;

    private String endpoint;
    private List<String> queues;
    private String takeStrategy = "fifo";
    private Integer takeSize = 10;
}
