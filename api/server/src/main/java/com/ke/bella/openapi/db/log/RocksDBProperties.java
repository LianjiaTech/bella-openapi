package com.ke.bella.openapi.db.log;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bella.log.rocksdb")
public class RocksDBProperties {
    private boolean enabled = false;
    private String basePath = "/pvc-mount/bella-logs";
    private long flushIntervalMs = 1000;
    private int retentionDays = 7;
    private int readerCacheTtlSeconds = 1800;
    private String readerSecondarySuffix = "_secondary";
    private String shardInterval = "1d";
    private String cleanupCron = "0 0 3 * * ?";
}
