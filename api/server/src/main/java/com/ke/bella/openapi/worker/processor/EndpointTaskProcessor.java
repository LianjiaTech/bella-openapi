package com.ke.bella.openapi.worker.processor;

import com.ke.bella.openapi.tables.pojos.ChannelDB;
import com.ke.bella.queue.TaskWrapper;

public interface EndpointTaskProcessor {
    String endpoint();
    TaskProcessResult execute(TaskWrapper taskWrapper, ChannelDB channel, TaskSlot slot);
}
