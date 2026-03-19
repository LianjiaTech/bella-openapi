package com.ke.bella.openapi.protocol.limiter.manager;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public class JedisQpsLimiterManager extends AbstractQpsLimiterManager {

    private final JedisPool jedisPool;

    public JedisQpsLimiterManager(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    @Override
    protected String doScriptLoad(String scriptContent) throws IOException {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.scriptLoad(scriptContent);
        }
    }

    @Override
    protected List<Object> execEvalsha(String sha, String key, int qpsLimit, long currentTimeMs) throws Exception {
        List<String> keys = Collections.singletonList(key);
        List<String> args = Arrays.asList(
                String.valueOf(qpsLimit),
                String.valueOf(currentTimeMs),
                String.valueOf(SEGMENT_SIZE_MS),
                String.valueOf(NUM_SEGMENTS)
        );
        try (Jedis jedis = jedisPool.getResource()) {
            try {
                //noinspection unchecked
                return (List<Object>) jedis.evalsha(sha, keys, args);
            } catch (JedisDataException e) {
                if(e.getMessage() != null && e.getMessage().startsWith("NOSCRIPT")) {
                    String newSha = loadScript();
                    //noinspection unchecked
                    return (List<Object>) jedis.evalsha(newSha, keys, args);
                }
                throw e;
            }
        }
    }

    @Override
    protected Map<String, String> doReadAllSegments(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hgetAll(key);
        }
    }

    @Override
    protected List<String> doScanKeys(int maxScan) {
        List<String> keys = new ArrayList<>();
        String cursor = "0";
        ScanParams scanParams = new ScanParams().match(QPS_KEY_PREFIX + "*").count(100);
        try (Jedis jedis = jedisPool.getResource()) {
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                cursor = scanResult.getCursor();
                for (String key : scanResult.getResult()) {
                    keys.add(key);
                    if(keys.size() >= maxScan) {
                        log.warn("getTopN scan limit reached: {}", maxScan);
                        return keys;
                    }
                }
            } while (!"0".equals(cursor));
        }
        return keys;
    }
}
