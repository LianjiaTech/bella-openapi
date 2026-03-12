package com.ke.bella.openapi.limiter;

import com.ke.bella.openapi.protocol.limiter.QpsCheckResult;
import com.ke.bella.openapi.protocol.limiter.manager.AbstractQpsLimiterManager;
import com.ke.bella.openapi.protocol.limiter.manager.JedisQpsLimiterManager;
import com.ke.bella.openapi.protocol.limiter.manager.RedissonQpsLimiterManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.redisson.api.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * QpsLimiterManager 实现类集成测试
 * 测试 Jedis 和 Redisson 两种 Redis 客户端实现
 */
@RunWith(MockitoJUnitRunner.class)
public class QpsLimiterManagerImplTest {

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RScript rScript;

    private TestJedisQpsLimiterManager jedisManager;
    private TestRedissonQpsLimiterManager redissonManager;

    @Before
    public void setUp() throws Exception {
        // Setup Jedis
        when(jedisPool.getResource()).thenReturn(jedis);
        jedisManager = new TestJedisQpsLimiterManager(jedisPool);
        setPrivateField(jedisManager, "enabled", true);
        setPrivateField(jedisManager, "defaultLimit", 200);

        // Setup Redisson
        when(redissonClient.getScript()).thenReturn(rScript);
        redissonManager = new TestRedissonQpsLimiterManager(redissonClient);
        setPrivateField(redissonManager, "enabled", true);
        setPrivateField(redissonManager, "defaultLimit", 200);
    }

    /**
     * 测试 Jedis 实现的完整限流流程
     */
    @Test
    public void testJedis_checkLimit_integration_shouldWork() throws Exception {
        // Mock Lua 脚本执行
        String sha = "abc123";
        setCachedSha(jedisManager, sha);

        List<Object> luaResult = Arrays.asList(1L, 80L); // allowed, currentQps=80
        when(jedis.evalsha(anyString(), anyList(), anyList())).thenReturn(luaResult);

        QpsCheckResult result = jedisManager.checkLimit("ak_test", 100);

        assertTrue(result.isAllowed());
        assertEquals(100, result.getLimit());
        assertEquals(80, result.getCurrentQps());
    }

    /**
     * 测试 Redisson 实现的完整限流流程
     */
    @Test
    public void testRedisson_checkLimit_integration_shouldWork() throws Exception {
        // Mock Lua 脚本执行
        String sha = "abc123";
        setCachedSha(redissonManager, sha);

        List<Object> luaResult = Arrays.asList(1L, 80L); // allowed, currentQps=80
        when(rScript.evalSha(eq(RScript.Mode.READ_WRITE), eq(sha), any(), anyList(), any()))
                .thenReturn(luaResult);

        QpsCheckResult result = redissonManager.checkLimit("ak_test", 100);

        assertTrue(result.isAllowed());
        assertEquals(100, result.getLimit());
        assertEquals(80, result.getCurrentQps());
    }

    // ========== Test Implementations ==========

    static class TestJedisQpsLimiterManager extends JedisQpsLimiterManager {
        private final JedisPool testJedisPool;

        public TestJedisQpsLimiterManager(JedisPool jedisPool) {
            super(jedisPool);  // 调用父类构造方法
            this.testJedisPool = jedisPool;
        }

        @Override
        protected String doScriptLoad(String scriptContent) throws java.io.IOException {
            try (Jedis jedis = testJedisPool.getResource()) {
                return jedis.scriptLoad(scriptContent);
            }
        }

        @Override
        protected List<Object> execEvalsha(String sha, String key, int qpsLimit, long currentTimeMs) throws Exception {
            List<String> keys = java.util.Collections.singletonList(key);
            List<String> args = Arrays.asList(
                    String.valueOf(qpsLimit),
                    String.valueOf(currentTimeMs),
                    String.valueOf(SEGMENT_SIZE_MS),
                    String.valueOf(NUM_SEGMENTS)
            );
            try (Jedis jedis = testJedisPool.getResource()) {
                //noinspection unchecked
                return (List<Object>) jedis.evalsha(sha, keys, args);
            }
        }

        @Override
        protected Map<String, String> doReadAllSegments(String key) {
            try (Jedis jedis = testJedisPool.getResource()) {
                return jedis.hgetAll(key);
            }
        }

        @Override
        protected List<String> doScanKeys(int maxScan) {
            return java.util.Collections.emptyList();
        }
    }

    static class TestRedissonQpsLimiterManager extends RedissonQpsLimiterManager {
        private final RedissonClient testRedisson;

        public TestRedissonQpsLimiterManager(RedissonClient redissonClient) {
            super(redissonClient);  // 调用父类构造方法
            this.testRedisson = redissonClient;
        }

        @Override
        protected String doScriptLoad(String scriptContent) throws java.io.IOException {
            return testRedisson.getScript().scriptLoad(scriptContent);
        }

        @Override
        protected List<Object> execEvalsha(String sha, String key, int qpsLimit, long currentTimeMs) throws Exception {
            List<Object> keys = java.util.Arrays.asList(key);
            List<Object> params = new java.util.ArrayList<>();
            params.add(qpsLimit);
            params.add(currentTimeMs);
            params.add(SEGMENT_SIZE_MS);
            params.add(NUM_SEGMENTS);
            RScript rScript = testRedisson.getScript();
            return rScript.evalSha(RScript.Mode.READ_WRITE, sha, RScript.ReturnType.VALUE, keys, params.toArray());
        }

        @Override
        protected Map<String, String> doReadAllSegments(String key) {
            return java.util.Collections.emptyMap();
        }

        @Override
        protected List<String> doScanKeys(int maxScan) {
            return java.util.Collections.emptyList();
        }
    }

    // ========== Helper Methods ==========

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = AbstractQpsLimiterManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setCachedSha(AbstractQpsLimiterManager manager, String sha) throws Exception {
        java.lang.reflect.Field field = AbstractQpsLimiterManager.class.getDeclaredField("scriptShaCache");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> cache = (Map<String, String>) field.get(manager);
        cache.put("lua/limiter/qps.lua", sha);
    }
}
