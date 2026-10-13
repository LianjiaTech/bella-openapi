package com.ke.bella.openapi.db;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class IDGenerator {
    private static String instanceId;
    public static final String DATE_PATTERN = "yyMMddHHmmss";
    private static final int MAX_COUNT = (int) 1e7;

    private final String prefix;
    private final int serialLength;
    private final String serialFormat;
    private final int serialMask;
    private final AtomicInteger serialCounter = new AtomicInteger(0);

    public IDGenerator(String prefix) {
        this(prefix, 6);
    }

    public IDGenerator(String prefix, int serialLength) {
        this.prefix = prefix;
        this.serialLength = serialLength;
        this.serialFormat = "%0" + this.serialLength + "d";
        this.serialMask = Integer.parseInt("1" + String.format(serialFormat, 0));
    }

    public String generateWithSpaceCodeHash(String spaceCode) {
        String now = new SimpleDateFormat(DATE_PATTERN).format(new Date());
        String serial = nextTick();
        String spaceCodeHash = String.valueOf(Math.abs(hashCode(spaceCode)));
        return String.format("%s%s%s%s-%s", prefix, now, instanceId, serial, spaceCodeHash);
    }

    private String nextTick() {
        int val = serialCounter.incrementAndGet();
        if(val >= MAX_COUNT) {
            synchronized(serialCounter) {
                val = serialCounter.get();
                if(val >= MAX_COUNT) {
                    serialCounter.set(0);
                }
            }
            val = serialCounter.incrementAndGet();
        }
        return String.format(this.serialFormat, val % this.serialMask);
    }

    public static void setInstanceId(Long id) {
        int idx = id.intValue();
        if(idx > 9999) {
            throw new IllegalStateException("超出当前所能够支持的最大实例数");
        }
        instanceId = String.format("%04d", idx);
    }

    public static int hashCode(String str) {
        if(str == null || str.isEmpty()) {
            return 0;
        }
        int h = 0;
        for (int i = 0; i < str.length(); i++) {
            h = 31 * h + str.charAt(i);
        }
        return h;
    }
}
