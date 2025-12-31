package com.ke.bella.openapi.protocol.limiter;

import com.ke.bella.openapi.script.LuaScriptExecutor;
import com.ke.bella.openapi.script.ScriptType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * LimiterManager并发测试
 * 
 * 测试重点：
 * 1. incrementConcurrentCount() 和 decrementConcurrentCount() 的并发安全性
 * 2. 分桶机制在高并发下的准确性
 * 3. 异常处理的线程安全性
 * 4. 长期运行的稳定性
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class LimiterManagerConcurrentTest {

    @Mock
    private LuaScriptExecutor executor;

    @Mock
    private RedissonClient redisson;

    @InjectMocks
    private LimiterManager limiterManager;

    private static final String TEST_AK_CODE = "test-ak-123";
    private static final String TEST_ENTITY_CODE = "gpt-4";
    
    // 并发测试参数
    private static final int THREAD_POOL_SIZE = 100;

    // 用于模拟分桶计数的原子计数器
    private final AtomicLong mockConcurrentCount = new AtomicLong(0);
    private final AtomicInteger executorCallCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws IOException {
        // 重置计数器
        mockConcurrentCount.set(0);
        executorCallCount.set(0);
        
        // 默认模拟Lua脚本执行，实现简单的计数逻辑
        lenient().when(executor.execute(eq("/concurrent"), eq(ScriptType.limiter), anyList(), anyList()))
            .thenAnswer(invocation -> {
                executorCallCount.incrementAndGet();
                List<Object> params = invocation.getArgument(3);
                String operation = (String) params.get(0);
                
                if ("INCR".equals(operation)) {
                    return mockConcurrentCount.incrementAndGet();
                } else if ("DECR".equals(operation)) {
                    long current = mockConcurrentCount.get();
                    return current > 0 ? mockConcurrentCount.decrementAndGet() : 0L;
                } else {
                    return mockConcurrentCount.get();
                }
            });
    }

    @Test
    void testConcurrentIncrement() throws InterruptedException {
        // Given - 并发INCR测试
        int threadCount = 50;
        int incrementsPerThread = 10;
        int expectedTotal = threadCount * incrementsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        
        // When - 并发执行INCR操作
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待同时开始
                    
                    for (int j = 0; j < incrementsPerThread; j++) {
                        limiterManager.incrementConcurrentCount(TEST_AK_CODE, TEST_ENTITY_CODE);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // 开始并发执行
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "并发INCR测试超时");
        
        executor.shutdown();
        
        // Then - 验证并发INCR的准确性
        assertEquals(expectedTotal, mockConcurrentCount.get(), 
                    "并发INCR后计数不正确: expected=" + expectedTotal + ", actual=" + mockConcurrentCount.get());
        
        log.info("并发INCR测试完成: {} 个线程 × {} 次操作 = {} 总计数", 
                threadCount, incrementsPerThread, expectedTotal);
    }

    @Test
    void testConcurrentDecrement() throws InterruptedException {
        // Given - 预先增加一些计数
        int initialCount = 100;
        mockConcurrentCount.set(initialCount);
        
        int threadCount = 20;
        int decrementsPerThread = 3;
        int expectedFinalCount = initialCount - (threadCount * decrementsPerThread);
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        
        // When - 并发执行DECR操作
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < decrementsPerThread; j++) {
                        limiterManager.decrementConcurrentCount(TEST_AK_CODE, TEST_ENTITY_CODE);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(completeLatch.await(10, TimeUnit.SECONDS), "并发DECR测试超时");
        
        executor.shutdown();
        
        // Then - 验证并发DECR的准确性
        assertEquals(expectedFinalCount, mockConcurrentCount.get(),
                    "并发DECR后计数不正确: expected=" + expectedFinalCount + ", actual=" + mockConcurrentCount.get());
        
        log.info("并发DECR测试完成: 初始计数={}, 减少={}, 最终计数={}", 
                initialCount, threadCount * decrementsPerThread, expectedFinalCount);
    }

    @Test
    void testMixedConcurrentOperations() throws InterruptedException {
        // Given - 混合并发操作测试
        int incrThreads = 30;
        int decrThreads = 20;
        int operationsPerThread = 5;
        
        long expectedFinalCount = (incrThreads - decrThreads) * operationsPerThread;
        
        ExecutorService executor = Executors.newFixedThreadPool(incrThreads + decrThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(incrThreads + decrThreads);
        
        // When - 同时执行INCR和DECR操作
        
        // 创建INCR线程
        for (int i = 0; i < incrThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        limiterManager.incrementConcurrentCount(TEST_AK_CODE, TEST_ENTITY_CODE);
                        Thread.sleep(1); // 增加一点随机性
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        // 创建DECR线程
        for (int i = 0; i < decrThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        limiterManager.decrementConcurrentCount(TEST_AK_CODE, TEST_ENTITY_CODE);
                        Thread.sleep(1); // 增加一点随机性
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(completeLatch.await(15, TimeUnit.SECONDS), "混合并发测试超时");
        
        executor.shutdown();
        
        // Then - 验证混合并发操作的最终结果
        long actualCount = mockConcurrentCount.get();
        
        log.info("混合并发测试完成: INCR线程={}, DECR线程={}, 每线程操作={}, 预期最终计数={}, 实际计数={}", 
                incrThreads, decrThreads, operationsPerThread, expectedFinalCount, actualCount);
        
        // 由于DECR不会让计数变成负数，所以实际计数应该>=0且接近预期值
        assertTrue(actualCount >= 0, "计数不能为负数");
        assertTrue(Math.abs(actualCount - expectedFinalCount) <= 5, 
                  "混合并发操作结果偏差过大");
    }

    @Test
    void testHighConcurrencyStressTest() throws InterruptedException {
        // Given - 高并发压力测试
        int threadCount = THREAD_POOL_SIZE;
        int operationsPerThread = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // When - 高并发压力测试
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            if (threadId % 2 == 0) {
                                limiterManager.incrementConcurrentCount(TEST_AK_CODE, TEST_ENTITY_CODE);
                            } else {
                                limiterManager.decrementConcurrentCount(TEST_AK_CODE, TEST_ENTITY_CODE);
                            }
                            successCount.incrementAndGet();
                            
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            log.warn("线程{}操作失败: {}", threadId, e.getMessage());
                        }
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completeLatch.countDown();
                }
            });
        }
        
        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        assertTrue(completeLatch.await(30, TimeUnit.SECONDS), "高并发压力测试超时");
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        
        int totalOperations = threadCount * operationsPerThread;
        
        log.info("高并发压力测试完成:");
        log.info("  线程数: {}", threadCount);
        log.info("  每线程操作数: {}", operationsPerThread);
        log.info("  总操作数: {}", totalOperations);
        log.info("  成功操作数: {}", successCount.get());
        log.info("  失败操作数: {}", errorCount.get());
        log.info("  执行时间: {}ms", endTime - startTime);
        log.info("  最终计数: {}", mockConcurrentCount.get());
        log.info("  平均操作时间: {}ms", (double)(endTime - startTime) / totalOperations);
        
        assertTrue(successCount.get() >= totalOperations * 0.95,
                  "操作成功率过低: " + (double)successCount.get() / totalOperations * 100 + "%");
        assertTrue(errorCount.get() < totalOperations * 0.05,
                  "错误率过高: " + (double)errorCount.get() / totalOperations * 100 + "%");
    }
}
