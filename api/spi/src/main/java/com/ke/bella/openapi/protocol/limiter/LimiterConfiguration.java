package com.ke.bella.openapi.protocol.limiter;

import com.ke.bella.openapi.protocol.limiter.manager.JedisQpsLimiterManager;
import com.ke.bella.openapi.protocol.limiter.manager.QpsLimiterManager;
import com.ke.bella.openapi.protocol.limiter.manager.RedissonQpsLimiterManager;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

/**
 * QPS 限流器配置类 统一管理 QPS 限流相关的 Bean 注册
 */
@Configuration
public class LimiterConfiguration {

    /**
     * QPS 限流器 - 根据可用的 Redis 客户端自动选择实现 优先使用 Redisson，其次使用 Jedis
     */
    @Bean
    public QpsLimiterManager qpsLimiterManager(
            @Autowired(required = false) RedissonClient redissonClient,
            @Autowired(required = false) JedisPool jedisPool) {

        if(redissonClient != null) {
            return new RedissonQpsLimiterManager(redissonClient);
        }

        if(jedisPool != null) {
            return new JedisQpsLimiterManager(jedisPool);
        }

        throw new IllegalStateException(
                "QPS Limiter requires either RedissonClient or JedisPool, but neither was found. " +
                        "Please configure Redis connection in application.yml");
    }

    /**
     * QPS 限流 AOP 切面 用于 @QpsRateLimit 注解的方法级限流
     */
    @Bean
    public QpsRateLimitAspect qpsRateLimitAspect(QpsLimiterManager qpsLimiterManager) {
        return new QpsRateLimitAspect(qpsLimiterManager);
    }

    /**
     * QPS 限流拦截器 在 Spring MVC 拦截器链中进行 QPS 限流
     */
    @Bean
    public QpsRateLimitInterceptor qpsRateLimitInterceptor(QpsLimiterManager qpsLimiterManager) {
        return new QpsRateLimitInterceptor(qpsLimiterManager);
    }
}
