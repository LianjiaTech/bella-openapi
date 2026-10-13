package com.ke.bella.openapi.db.log;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ke.bella.openapi.server.BellaServerContextHolder;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "bella.log.rocksdb", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RocksDBProperties.class)
public class RocksDBLogReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private final RocksDBProperties properties;
    private final RocksDBResourceFactory resources;
    private final Cache<String, CachedDB> dbCache;
    private final String readerInstanceId;
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();
    private boolean destroyed;

    static {
        RocksDB.loadLibrary();
    }

	@Autowired
    public RocksDBLogReader(RocksDBProperties properties) {
        this(properties, new RocksDBResourceFactory());
    }

    RocksDBLogReader(RocksDBProperties properties, RocksDBResourceFactory resources) {
        this.properties = properties;
        this.resources = resources;
        String ip = BellaServerContextHolder.getIp();
        if (ip == null) ip = "unknown";
        this.readerInstanceId = "reader-" + ip.replace(".", "-") + "-" + Integer.toHexString(System.identityHashCode(this));
        this.dbCache = Caffeine.<String, CachedDB>newBuilder()
                .expireAfterAccess(properties.getReaderCacheTtlSeconds(), TimeUnit.SECONDS)
                .executor(Runnable::run)
                .removalListener((String key, CachedDB cachedDB, RemovalCause cause) -> {
                    if (cachedDB != null) {
                        cachedDB.close();
                        log.debug("Closed Secondary RocksDB handle for {}", key);
                    }
                })
                .build();
        log.info("RocksDBLogReader initialized with readerInstanceId: {}", readerInstanceId);
    }

    public Map<String, Object> readByShardPath(String requestId, String shardPath) {
        if (requestId == null || shardPath == null) {
            return null;
        }
        lifecycleLock.readLock().lock();
        try {
            if (destroyed) {
                return null;
            }
            String dbDir = properties.getBasePath() + File.separator + shardPath.replace("/", File.separator);
            if (dbDir.endsWith(File.separator)) {
                dbDir = dbDir.substring(0, dbDir.length() - 1);
            }
            byte[] keyBytes = requestId.getBytes(StandardCharsets.UTF_8);

            byte[] value = getFromDB(shardPath, dbDir, keyBytes);
            if (value != null) {
                return deserialize(value);
            }
            return null;
        } finally {
            lifecycleLock.readLock().unlock();
        }
    }

    public void invalidateCache(String pathPrefix) {
        lifecycleLock.writeLock().lock();
        try {
            if (destroyed) {
                return;
            }
            dbCache.asMap().entrySet().removeIf(entry -> entry.getKey().startsWith(pathPrefix));
            dbCache.cleanUp();
        } finally {
            lifecycleLock.writeLock().unlock();
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
            dbCache.invalidateAll();
            dbCache.cleanUp();
        } finally {
            lifecycleLock.writeLock().unlock();
        }
        log.info("RocksDBLogReader destroyed, all handles closed");
    }

    private byte[] getFromDB(String cacheKey, String dbPath, byte[] keyBytes) {
        try {
            CachedDB cachedDB = dbCache.get(cacheKey, k -> openAsSecondary(dbPath));
            if (cachedDB == null) {
                return null;
            }
            return cachedDB.get(keyBytes);
        } catch (RocksDBException e) {
            log.warn("RocksDB read error in {}: {}", dbPath, e.getMessage());
            dbCache.invalidate(cacheKey);
            dbCache.cleanUp();
            return null;
        }
    }

    private CachedDB openAsSecondary(String dbPath) {
        File dir = new File(dbPath);
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }
        String secondaryPath = dbPath + properties.getReaderSecondarySuffix() + "_" + readerInstanceId;
        File secondaryDir = new File(secondaryPath);
        if (!secondaryDir.exists() && !secondaryDir.mkdirs()) {
            return null;
        }
        Options options = null;
        BloomFilter bloomFilter = null;
        RocksDB db = null;
        try {
            options = resources.createOptions();
            options.setCreateIfMissing(false);
            BlockBasedTableConfig tableConfig = resources.createTableConfig();
            bloomFilter = resources.createBloomFilter();
            tableConfig.setFilterPolicy(bloomFilter);
            options.setTableFormatConfig(tableConfig);
            db = resources.openAsSecondary(options, dbPath, secondaryPath);
            CachedDB cachedDB = new CachedDB(db, options, bloomFilter);
            db = null;
            options = null;
            bloomFilter = null;
            return cachedDB;
        } catch (Exception e) {
            log.warn("Failed to open RocksDB Secondary at {}: {}", dbPath, e.getMessage());
            return null;
        } finally {
            closeQuietly(db);
            closeQuietly(options);
            closeQuietly(bloomFilter);
        }
    }

    private Map<String, Object> deserialize(byte[] value) {
        try {
            String json = new String(value, StandardCharsets.UTF_8);
            return JacksonUtils.deserialize(json, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Failed to deserialize RocksDB value: {}", e.getMessage());
            return null;
        }
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

    static class CachedDB implements AutoCloseable {
        final RocksDB db;
        final Options options;
        final BloomFilter bloomFilter;
        private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        private boolean closed;

        CachedDB(RocksDB db, Options options, BloomFilter bloomFilter) {
            this.db = db;
            this.options = options;
            this.bloomFilter = bloomFilter;
        }

        byte[] get(byte[] keyBytes) throws RocksDBException {
            lock.readLock().lock();
            try {
                if (closed) {
                    return null;
                }
                db.tryCatchUpWithPrimary();
                return db.get(keyBytes);
            } finally {
                lock.readLock().unlock();
            }
        }

        @Override
        public void close() {
            lock.writeLock().lock();
            try {
                if (closed) {
                    return;
                }
                closed = true;
                closeQuietly(db);
                closeQuietly(options);
                closeQuietly(bloomFilter);
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

}
