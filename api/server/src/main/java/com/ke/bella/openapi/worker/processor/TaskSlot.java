package com.ke.bella.openapi.worker.processor;

import java.util.concurrent.atomic.AtomicBoolean;

public class TaskSlot implements Runnable {
    private final Runnable releaseSlot;
    private final AtomicBoolean released = new AtomicBoolean(false);

    public TaskSlot(Runnable releaseSlot) {
        this.releaseSlot = releaseSlot;
    }

    public void release() {
        if(released.compareAndSet(false, true)) {
            releaseSlot.run();
        }
    }

    @Override
    public void run() {
        release();
    }
}
