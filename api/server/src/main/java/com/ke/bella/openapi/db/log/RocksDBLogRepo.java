package com.ke.bella.openapi.db.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.server.BellaServerContextHolder;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "bella.log.rocksdb", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RocksDBProperties.class)
public class RocksDBLogRepo implements LogRepo {

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("^(\\d+)([mhd])$");

    private final RocksDBProperties properties;
    private final AtomicReference<DBHolder> currentDB = new AtomicReference<>();
    private volatile String instanceId;
    private volatile long shardDurationMinutes;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDBLogRepo(RocksDBProperties properties) {
        this.properties = properties;
        this.shardDurationMinutes = parseShardIntervalMinutes(properties.getShardInterval());
    }

    @PostConstruct
    public void init() {
        String ip = BellaServerContextHolder.getIp();
        if (ip == null) {
            ip = "unknown";
        }
        this.instanceId = "inst-" + ip.replace(".", "-") + "-" + Integer.toHexString(System.identityHashCode(this));
        openDBForShard(computeShardId(LocalDateTime.now()));
        log.info("RocksDBLogRepo initialized, instanceId={}, basePath={}, shardInterval={}", instanceId, properties.getBasePath(), properties.getShardInterval());
    }

    @Override
    public void record(EndpointProcessData logData) {
        try {
            String shardId = computeShardId(LocalDateTime.now());
            DBHolder holder = getOrRollDB(shardId);
            if (holder == null) {
                return;
            }
            String key = logData.getRequestId();
            if (key == null) {
                return;
            }
            String shardPath = shardId + "/" + instanceId + "/";
            logData.setShardPath(shardPath);

            String json = JacksonUtils.serialize(logData);
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = json.getBytes(StandardCharsets.UTF_8);
            holder.db.put(holder.writeOptions, keyBytes, valueBytes);
        } catch (Exception e) {
            log.warn("RocksDB write failed for requestId={}: {}", logData.getRequestId(), e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${bella.log.rocksdb.flush-interval-ms:1000}")
    public void flush() {
        DBHolder holder = currentDB.get();
        if (holder != null) {
            try {
                holder.db.flush(new FlushOptions().setWaitForFlush(false));
            } catch (RocksDBException e) {
                log.warn("RocksDB flush failed: {}", e.getMessage());
            }
        }
    }

    public String getCurrentShardPath() {
        DBHolder holder = currentDB.get();
        return holder != null ? holder.shardId + "/" + instanceId + "/" : null;
    }

    @PreDestroy
    public void destroy() {
        DBHolder holder = currentDB.getAndSet(null);
        if (holder != null) {
            holder.close();
        }
        log.info("RocksDBLogRepo destroyed");
    }

    private DBHolder getOrRollDB(String shardId) {
        DBHolder holder = currentDB.get();
        if (holder != null && holder.shardId.equals(shardId)) {
            return holder;
        }
        synchronized (this) {
            holder = currentDB.get();
            if (holder != null && holder.shardId.equals(shardId)) {
                return holder;
            }
            DBHolder oldHolder = holder;
            openDBForShard(shardId);
            if (oldHolder != null) {
                oldHolder.close();
            }
            return currentDB.get();
        }
    }

    private void openDBForShard(String shardId) {
        String dbDir = properties.getBasePath() + File.separator + shardId + File.separator + instanceId;
        File dir = new File(dbDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            Options options = new Options();
            options.setCreateIfMissing(true);
            options.setCompressionType(CompressionType.SNAPPY_COMPRESSION);

            BlockBasedTableConfig tableConfig = new BlockBasedTableConfig();
            tableConfig.setFilterPolicy(new BloomFilter(10, false));
            options.setTableFormatConfig(tableConfig);

            WriteOptions writeOptions = new WriteOptions();
            writeOptions.setDisableWAL(false);

            RocksDB db = RocksDB.open(options, dbDir);
            currentDB.set(new DBHolder(db, writeOptions, options, shardId));
            log.info("Opened RocksDB at {}", dbDir);
        } catch (RocksDBException e) {
            log.error("Failed to open RocksDB at {}: {}", dbDir, e.getMessage());
        }
    }

    String computeShardId(LocalDateTime now) {
        if (shardDurationMinutes >= 1440) {
            return now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else if (shardDurationMinutes >= 60) {
            return now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HH"));
        } else {
            int minute = (now.getMinute() / (int) shardDurationMinutes) * (int) shardDurationMinutes;
            return now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HH")) + String.format("%02d", minute);
        }
    }

    private static long parseShardIntervalMinutes(String interval) {
        Matcher m = INTERVAL_PATTERN.matcher(interval.trim().toLowerCase());
        if (!m.matches()) {
            return 1440; // default 1 day
        }
        long value = Long.parseLong(m.group(1));
        switch (m.group(2)) {
            case "m": return value;
            case "h": return value * 60;
            case "d": return value * 1440;
            default: return 1440;
        }
    }

    private static class DBHolder {
        final RocksDB db;
        final WriteOptions writeOptions;
        final Options options;
        final String shardId;

        DBHolder(RocksDB db, WriteOptions writeOptions, Options options, String shardId) {
            this.db = db;
            this.writeOptions = writeOptions;
            this.options = options;
            this.shardId = shardId;
        }

        void close() {
            try {
                db.close();
            } catch (Exception e) {
                // ignore
            }
            writeOptions.close();
            options.close();
        }
    }
}
