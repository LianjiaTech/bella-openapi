package com.ke.bella.openapi.worker;

import com.ke.bella.queue.QueueMode;

public interface WorkerService {

    QueueMode workerMode();

    String queueName();

    void start();

    void stop();

    boolean isStopped();

}
