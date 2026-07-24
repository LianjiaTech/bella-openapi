package com.ke.bella.openapi.db.log;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ke.bella.openapi.server.BellaServerContextHolder;
import com.ke.bella.openapi.utils.JacksonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "bella.log.rocksdb", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RocksDBProperties.class)
public class RocksDBLogReader {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private final RocksDBProperties properties;
    private final Cache<String, CachedDB> dbCache;
    private final String readerInstanceId;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDBLogReader(RocksDBProperties properties) {
        this.properties = properties;
        String ip = BellaServerContextHolder.getIp();
        if (ip == null) ip = "unknown";
        this.readerInstanceId = "reader-" + ip.replace(".", "-") + "-" + Integer.toHexString(System.identityHashCode(this));
        this.dbCache = Caffeine.newBuilder()
                .expireAfterAccess(properties.getReaderCacheTtlSeconds(), TimeUnit.SECONDS)
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
    }

    public void invalidateCache(String pathPrefix) {
        dbCache.asMap().entrySet().removeIf(entry -> entry.getKey().startsWith(pathPrefix));
    }

    @PreDestroy
    public void destroy() {
        dbCache.invalidateAll();
        dbCache.cleanUp();
        log.info("RocksDBLogReader destroyed, all handles closed");
    }

    private byte[] getFromDB(String cacheKey, String dbPath, byte[] keyBytes) {
        try {
            CachedDB cachedDB = dbCache.get(cacheKey, k -> openAsSecondary(dbPath));
            if (cachedDB == null) {
                return null;
            }
            cachedDB.db.tryCatchUpWithPrimary();
            return cachedDB.db.get(keyBytes);
        } catch (RocksDBException e) {
            log.warn("RocksDB read error in {}: {}", dbPath, e.getMessage());
            dbCache.invalidate(cacheKey);
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
        if (!secondaryDir.exists()) {
            secondaryDir.mkdirs();
        }
        try {
            Options options = new Options();
            options.setCreateIfMissing(false);
            BlockBasedTableConfig tableConfig = new BlockBasedTableConfig();
            tableConfig.setFilterPolicy(new BloomFilter(10, false));
            options.setTableFormatConfig(tableConfig);
            RocksDB db = RocksDB.openAsSecondary(options, dbPath, secondaryPath);
            return new CachedDB(db, options);
        } catch (RocksDBException e) {
            log.warn("Failed to open RocksDB Secondary at {}: {}", dbPath, e.getMessage());
            return null;
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

    private static class CachedDB {
        final RocksDB db;
        final Options options;

        CachedDB(RocksDB db, Options options) {
            this.db = db;
            this.options = options;
        }

        void close() {
            try {
                db.close();
            } catch (Exception e) {
                // ignore
            }
            options.close();
        }
    }
}
