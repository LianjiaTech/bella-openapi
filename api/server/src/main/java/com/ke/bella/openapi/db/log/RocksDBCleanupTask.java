package com.ke.bella.openapi.db.log;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "bella.log.rocksdb", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RocksDBProperties.class)
public class RocksDBCleanupTask {

    private static final DateTimeFormatter SHARD_ID_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("uuuuMMdd-HH")
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    private final RocksDBProperties properties;
    private final RocksDBLogReader logReader;

    public RocksDBCleanupTask(RocksDBProperties properties, RocksDBLogReader logReader) {
        this.properties = properties;
        this.logReader = logReader;
    }

    @Scheduled(cron = "${bella.log.rocksdb.cleanup-cron:0 0 * * * ?}")
    public void cleanup() {
        cleanup(LocalDateTime.now());
    }

    void cleanup(LocalDateTime now) {
        File baseDir = new File(properties.getBasePath());
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return;
        }
        LocalDateTime cutoff = now.minusHours(properties.getEffectiveRetentionHours());
        File[] shardDirs = baseDir.listFiles(File::isDirectory);
        if (shardDirs == null) {
            return;
        }
        for (File shardDir : shardDirs) {
            String name = shardDir.getName();
            if (name.startsWith("__")) {
                continue;
            }
            try {
                LocalDateTime shardStart = LocalDateTime.parse(name, SHARD_ID_FORMATTER);
                LocalDateTime shardEnd = shardStart.plusHours(1);
                if (!shardEnd.isAfter(cutoff)) {
                    logReader.invalidateCache(name + "/");
                    FileUtils.deleteDirectory(shardDir);
                    log.info("Cleaned up expired RocksDB shard directory: {}", shardDir.getAbsolutePath());
                    File parentDir = shardDir.getParentFile();
                    if (parentDir != null) {
                        String prefix = shardDir.getName() + properties.getReaderSecondarySuffix();
                        File[] secondaryDirs = parentDir.listFiles((dir, n) -> n.startsWith(prefix));
                        if (secondaryDirs != null) {
                            for (File secDir : secondaryDirs) {
                                FileUtils.deleteDirectory(secDir);
                                log.info("Cleaned up secondary directory: {}", secDir.getAbsolutePath());
                            }
                        }
                    }
                }
            } catch (DateTimeParseException e) {
                // not a shard directory, skip
            } catch (IOException e) {
                log.warn("Failed to delete directory {}: {}", shardDir.getAbsolutePath(), e.getMessage());
            }
        }
    }
}
