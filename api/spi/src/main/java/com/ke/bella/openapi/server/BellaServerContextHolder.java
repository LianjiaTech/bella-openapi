package com.ke.bella.openapi.server;

import java.util.concurrent.atomic.AtomicReference;

public class BellaServerContextHolder {
    private static final AtomicReference<BellaServerContext> CONTEXT = new AtomicReference<>();

    public static void setContext(BellaServerContext context) {
        CONTEXT.set(context);
    }

    public static BellaServerContext getContext() {
        return CONTEXT.get();
    }

    public static String getIp() {
        BellaServerContext context = getContext();
        return context != null ? context.getIp() : null;
    }

    public static Integer getPort() {
        BellaServerContext context = getContext();
        return context != null ? context.getPort() : null;
    }

    public static String getApplicationName() {
        BellaServerContext context = getContext();
        return context != null ? context.getApplicationName() : null;
    }
}
