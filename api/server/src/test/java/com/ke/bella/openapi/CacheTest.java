package com.ke.bella.openapi;

import com.ke.bella.openapi.db.repo.ApikeyCostRepo;
import com.ke.bella.openapi.service.ApikeyService;
import com.ke.bella.openapi.utils.DateTimeUtils;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringJUnitConfig(TestConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = { "spring.profiles.active=dev"})
public class CacheTest {
    @Autowired
    private ApikeyService apikeyService;
    @Autowired
    private ApikeyCostRepo apikeyCostRepo;
    @Autowired
    private CacheRepoTest cacheRepoTest;
    @Autowired
    private RedissonClient redissonClient;

    public void testConcurrent() throws InterruptedException {
        String apikey = "计费测试code";
        String month = DateTimeUtils.getCurrentMonth();
        cacheRepoTest.deleteCost(apikey, month);
        Runnable runnable = () -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            apikeyService.recordCost(apikey, month, BigDecimal.ONE);
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            apikeyService.recordCost(apikey, month, BigDecimal.ONE);
        };
        for(int i = 0; i <= 4; i++) {
            new Thread(runnable).start();
        }
        Thread.sleep(2000);
        BigDecimal cost = apikeyService.loadCost(apikey, month);
        Assert.assertTrue("failed !!", 5 == cost.doubleValue());
        Thread.sleep(3000);
        cost = apikeyService.loadCost(apikey, month);
        Assert.assertTrue("failed !!", 10 == cost.doubleValue());
    }

    public void deleteCache() {
        //替换为真实ak code
        String akCode = "ak-test-placeholder-for-cache-deletion";
        String month = DateTimeUtils.getCurrentMonth();
        cacheRepoTest.deleteCost(akCode, month);
    }

    public void testJetCache() throws InterruptedException {
        String apikey = "计费测试code";
        String month = DateTimeUtils.getCurrentMonth();
        cacheRepoTest.delete(apikey, month);
        System.out.println(cacheRepoTest.get(apikey, month));
        cacheRepoTest.add(apikey, month, BigDecimal.ONE);
        System.out.println(cacheRepoTest.get(apikey, month));
        cacheRepoTest.add(apikey, month, BigDecimal.ONE);
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        cacheRepoTest.add(apikey, month, BigDecimal.ONE);
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, month));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, "2024-07"));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, "2024-07"));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, "2024-07"));
        Thread.sleep(1000);
        System.out.println(cacheRepoTest.get(apikey, "2024-07"));
        System.out.println(redissonClient.getBucket("bella-openapi-testCache:"+apikey+":"+month).isExists());
    }
}
