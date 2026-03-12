package com.ke.bella.openapi.protocol.limiter.manager;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class RedissonQpsLimiterManager extends AbstractQpsLimiterManager {

    private final RedissonClient redisson;

    public RedissonQpsLimiterManager(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    protected String doScriptLoad(String scriptContent) throws IOException {
        return redisson.getScript().scriptLoad(scriptContent);
    }

    @Override
    protected List<Object> execEvalsha(String sha, String key, int qpsLimit, long currentTimeMs) throws Exception {
        List<Object> keys = Lists.newArrayList(key);
        List<Object> params = new ArrayList<>();
        params.add(qpsLimit);
        params.add(currentTimeMs);
        params.add(SEGMENT_SIZE_MS);
        params.add(NUM_SEGMENTS);
        try {
            RScript rScript = redisson.getScript();
            return rScript.evalSha(RScript.Mode.READ_WRITE, sha, RScript.ReturnType.VALUE, keys, params.toArray());
        } catch (Exception e) {
            if(e.getMessage() != null && e.getMessage().contains("NOSCRIPT")) {
                String newSha = loadScript();
                RScript rScript = redisson.getScript();
                return rScript.evalSha(RScript.Mode.READ_WRITE, newSha, RScript.ReturnType.VALUE, keys, params.toArray());
            }
            throw e;
        }
    }

    @Override
    protected Map<String, String> doReadAllSegments(String key) {
        RMap<String, String> hashMap = redisson.getMap(key);
        return hashMap.readAllMap();
    }

    @Override
    protected List<String> doScanKeys(int maxScan) {
        List<String> keys = new ArrayList<>();
        for (String key : redisson.getKeys().getKeysByPattern(QPS_KEY_PREFIX + "*")) {
            keys.add(key);
            if(keys.size() >= maxScan) {
                log.warn("getTopN scan limit reached: {}", maxScan);
                break;
            }
        }
        return keys;
    }
}
