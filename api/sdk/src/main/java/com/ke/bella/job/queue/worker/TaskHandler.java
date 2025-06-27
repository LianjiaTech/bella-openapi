package com.ke.bella.job.queue.worker;

public interface TaskHandler {

    void execute(Task task);

}
