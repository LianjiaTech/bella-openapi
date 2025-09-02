package com.ke.bella.queue.worker;

import com.ke.bella.queue.TaskWrapper;

public interface TaskExecutor {
    void submit(TaskWrapper task);

    default Integer remainingCapacity() {
        return 1;
    }
}
