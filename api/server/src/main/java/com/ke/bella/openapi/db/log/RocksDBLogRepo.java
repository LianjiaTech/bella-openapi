package com.ke.bella.openapi.db.log;

import com.ke.bella.openapi.EndpointProcessData;
import com.ke.bella.openapi.server.BellaServerContextHolder;
import com.ke.bella.openapi.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "bella.log.rocksdb", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RocksDBProperties.class)
public class RocksDBLogRepo implements LogRepo {

    private static final DateTimeFormatter SHARD_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HH");

    private final RocksDBProperties properties;
    private final RocksDBResourceFactory resources;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private DBHolder currentDB;
    private volatile String instanceId;
    private boolean destroyed;

    static {
        RocksDB.loadLibrary();
    }

	@Autowired
    public RocksDBLogRepo(RocksDBProperties properties) {
        this(properties, new RocksDBResourceFactory());
    }

    RocksDBLogRepo(RocksDBProperties properties, RocksDBResourceFactory resources) {
        this.properties = properties;
        this.resources = resources;
    }

    @PostConstruct
    public void init() {
        String ip = BellaServerContextHolder.getIp();
        if (ip == null) {
            ip = "unknown";
        }
        this.instanceId = "inst-" + ip.replace(".", "-") + "-" + Integer.toHexString(System.identityHashCode(this));
        currentDB = openDBForShard(computeShardId(LocalDateTime.now()));
        log.info("RocksDBLogRepo initialized, instanceId={}, basePath={}", instanceId, properties.getBasePath());
    }

    @Override
    public void record(EndpointProcessData logData) {
        String shardId = computeShardId(LocalDateTime.now());
        if (!ensureShard(shardId)) {
            return;
        }
        lifecycleLock.readLock().lock();
        try {
            DBHolder holder = currentDB;
            if (destroyed || holder == null || !holder.shardId.equals(shardId)) {
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
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @Scheduled(fixedDelayString = "${bella.log.rocksdb.flush-interval-ms:1000}")
    public void flush() {
        lifecycleLock.readLock().lock();
        try {
            DBHolder holder = currentDB;
            if (destroyed || holder == null) {
                return;
            }
            try (FlushOptions options = resources.createFlushOptions().setWaitForFlush(false)) {
                holder.db.flush(options);
            } catch (RocksDBException e) {
                log.warn("RocksDB flush failed: {}", e.getMessage());
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public String getCurrentShardPath() {
        lifecycleLock.readLock().lock();
        try {
            DBHolder holder = currentDB;
            return !destroyed && holder != null ? holder.shardId + "/" + instanceId + "/" : null;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    @PreDestroy
    public void destroy() {
        lifecycleLock.writeLock().lock();
        try {
            if (destroyed) {
                return;
            }
            destroyed = true;
            closeQuietly(currentDB);
            currentDB = null;
        } finally {
            lifecycleLock.writeLock().unlock();
        }
        log.info("RocksDBLogRepo destroyed");
    }

    private boolean ensureShard(String shardId) {
        lifecycleLock.readLock().lock();
        try {
            if (destroyed) {
                return false;
            }
            if (currentDB != null && currentDB.shardId.equals(shardId)) {
                return true;
            }
        } finally {
            lifecycleLock.readLock().unlock();
        }

        lifecycleLock.writeLock().lock();
        try {
            if (destroyed) {
                return false;
            }
            if (currentDB != null && currentDB.shardId.equals(shardId)) {
                return true;
            }
            DBHolder candidate = openDBForShard(shardId);
            if (candidate == null) {
                return false;
            }
            DBHolder oldHolder = currentDB;
            currentDB = candidate;
            closeQuietly(oldHolder);
            return true;
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private DBHolder openDBForShard(String shardId) {
        String dbDir = properties.getBasePath() + File.separator + shardId + File.separator + instanceId;
        File dir = new File(dbDir);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        Options options = null;
        WriteOptions writeOptions = null;
        BloomFilter bloomFilter = null;
        RocksDB db = null;
        try {
            options = resources.createOptions();
            options.setCreateIfMissing(true);
            options.setCompressionType(CompressionType.SNAPPY_COMPRESSION);

            BlockBasedTableConfig tableConfig = resources.createTableConfig();
            bloomFilter = resources.createBloomFilter();
            tableConfig.setFilterPolicy(bloomFilter);
            options.setTableFormatConfig(tableConfig);

            writeOptions = resources.createWriteOptions();
            writeOptions.setDisableWAL(false);

            db = resources.open(options, dbDir);
            DBHolder holder = new DBHolder(db, writeOptions, options, bloomFilter, shardId);
            db = null;
            writeOptions = null;
            options = null;
            bloomFilter = null;
            log.info("Opened RocksDB at {}", dbDir);
            return holder;
        } catch (Exception e) {
            log.error("Failed to open RocksDB at {}: {}", dbDir, e.getMessage());
            return null;
        } finally {
            closeQuietly(db);
            closeQuietly(writeOptions);
            closeQuietly(options);
            closeQuietly(bloomFilter);
        }
    }

    String computeShardId(LocalDateTime now) {
        return now.format(SHARD_ID_FORMATTER);
    }

    private static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception ignored) {
            // ignore
        }
    }

    static class DBHolder implements AutoCloseable {
        final RocksDB db;
        final WriteOptions writeOptions;
        final Options options;
        final BloomFilter bloomFilter;
        final String shardId;

        DBHolder(RocksDB db, WriteOptions writeOptions, Options options, BloomFilter bloomFilter, String shardId) {
            this.db = db;
            this.writeOptions = writeOptions;
            this.options = options;
            this.bloomFilter = bloomFilter;
            this.shardId = shardId;
        }

        @Override
        public void close() {
            closeQuietly(db);
            closeQuietly(writeOptions);
            closeQuietly(options);
            closeQuietly(bloomFilter);
        }
    }
}
