package com.ke.bella.openapi.db.log;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "bella.log.rocksdb", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RocksDBProperties.class)
public class RocksDBCleanupTask {

    private static final DateTimeFormatter DATE_ONLY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RocksDBProperties properties;
    private final RocksDBLogReader logReader;

    public RocksDBCleanupTask(RocksDBProperties properties, RocksDBLogReader logReader) {
        this.properties = properties;
        this.logReader = logReader;
    }

    @Scheduled(cron = "${bella.log.rocksdb.cleanup-cron:0 0 3 * * ?}")
    public void cleanup() {
        File baseDir = new File(properties.getBasePath());
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            return;
        }
        LocalDate cutoff = LocalDate.now().minusDays(properties.getRetentionDays());
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
                String dateStr = name.length() >= 8 ? name.substring(0, 8) : name;
                LocalDate dirDate = LocalDate.parse(dateStr, DATE_ONLY_FMT);
                if (dirDate.isBefore(cutoff)) {
                    logReader.invalidateCache(name);
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
