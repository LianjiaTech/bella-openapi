package com.ke.bella.openapi.worker;

import com.ke.bella.queue.WorkerMode;

public interface WorkerService {

    WorkerMode workerMode();

    String queueName();

    void start();

    void stop();

    boolean isStopped();

}
