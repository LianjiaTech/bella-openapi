package com.ke.bella.openapi.protocol.completion;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.Protocol;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.SdkEventLoopGroup;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.ToString;

@Slf4j
public class AwsClientManager {

    private static final Map<String, ConcurrentHashMap<String, BedrockRuntimeClient>> httpCache = new ConcurrentHashMap<>();

    // 使用 Caffeine Cache 自动管理 asyncClient 生命周期，避免无限制增长
    // 配置：30分钟无访问自动过期，最多缓存100个客户端，过期时自动关闭释放资源
    private static final Cache<String, BedrockRuntimeAsyncClient> asyncCache = Caffeine.newBuilder()
            .maximumSize(20)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .removalListener((String key, BedrockRuntimeAsyncClient client, RemovalCause cause) -> {
                if (client != null) {
                    try {
                        client.close();
                        log.info("Closed AWS async client for key: {}, cause: {}", key, cause);
                    } catch (Exception e) {
                        log.warn("Failed to close AWS async client for key: {}", key, e);
                    }
                }
            })
            .build();

    private static final Map<String, AwsAuthorizationProvider> authCache = new ConcurrentHashMap<>();

    // 共享 EventLoopGroup，避免为每个客户端创建独立线程池
    // 配置 4 个线程，足够处理大多数并发场景，减少堆外内存占用
    private static final SdkEventLoopGroup SHARED_EVENT_LOOP_GROUP = SdkEventLoopGroup.create(new NioEventLoopGroup(4));

    // JVM 关闭时优雅释放所有资源
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down AWS client manager...");
            try {
                // 关闭所有异步客户端
                asyncCache.invalidateAll();
                asyncCache.cleanUp();

                // 关闭共享 EventLoopGroup
                SHARED_EVENT_LOOP_GROUP.eventLoopGroup().shutdownGracefully(100, 5000, TimeUnit.MILLISECONDS).sync();
                log.info("AWS client manager shutdown completed");
            } catch (Exception e) {
                log.error("Error during AWS client manager shutdown", e);
            }
        }, "aws-client-manager-shutdown"));
    }

    public static BedrockRuntimeClient client(String region, String endpoint, String accessKeyId, String secretKey) {
        return httpCache.computeIfAbsent(region, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(accessKeyId, k -> BedrockRuntimeClient.builder()
                        .endpointOverride(URI.create(endpoint))
                        .credentialsProvider(provide(accessKeyId, secretKey))
                        .region(Region.of(region))
                        .httpClient(ApacheHttpClient.builder()
                                .buildWithDefaults(AttributeMap.builder()
                                        .put(SdkHttpConfigurationOption.PROTOCOL, Protocol.HTTP1_1)
                                        .put(SdkHttpConfigurationOption.READ_TIMEOUT, Duration.ofSeconds(300))
                                        .build()))
                        .overrideConfiguration(ClientOverrideConfiguration.builder()
                                .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                                .apiCallTimeout(Duration.of(300, ChronoUnit.SECONDS))
                                .apiCallAttemptTimeout(Duration.of(300, ChronoUnit.SECONDS))
                                .build())
                        .build());
    }

    public static BedrockRuntimeAsyncClient asyncClient(String region, String endpoint, String accessKeyId, String secretKey) {
        String cacheKey = region + ":" + accessKeyId;
        return asyncCache.get(cacheKey, key -> {
            log.info("Creating new AWS async client for region: {}, accessKeyId: {}", region, accessKeyId);
            return BedrockRuntimeAsyncClient.builder()
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(provide(accessKeyId, secretKey))
                    .region(Region.of(region))
                    .httpClient(NettyNioAsyncHttpClient.builder()
                            // 使用共享 EventLoopGroup，大幅减少线程和堆外内存占用
                            .eventLoopGroup(SHARED_EVENT_LOOP_GROUP)
                            // 限制每个客户端的最大并发连接数，防止堆外内存无限增长
                            .maxConcurrency(50)
                            // 限制等待获取连接的最大请求数
                            .maxPendingConnectionAcquires(100)
                            // 连接超时配置
                            .connectionTimeout(Duration.ofSeconds(30))
                            .connectionAcquisitionTimeout(Duration.ofSeconds(10))
                            .buildWithDefaults(AttributeMap.builder()
                                    .put(SdkHttpConfigurationOption.PROTOCOL, Protocol.HTTP1_1)
                                    .put(SdkHttpConfigurationOption.READ_TIMEOUT, Duration.ofSeconds(120))
                                    .build()))
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                            .apiCallTimeout(Duration.of(300, ChronoUnit.SECONDS))
                            .apiCallAttemptTimeout(Duration.of(300, ChronoUnit.SECONDS))
                            .build())
                    .build();
        });
    }

    private static AwsAuthorizationProvider provide(String accessKeyId, String secretKey) {
        return authCache.computeIfAbsent(accessKeyId, k ->  new AwsAuthorizationProvider(accessKeyId, secretKey));
    }

    public static class AwsAuthorizationProvider implements AwsCredentialsProvider {

        private static final String PROVIDER_NAME = "SpringPropertyCredentialsProvider";

        private final String accessKeyId;
        private final String secretKey;

        private AwsAuthorizationProvider(String accessKeyId, String secretKey) {
            this.accessKeyId = accessKeyId;
            this.secretKey = secretKey;
        }

        @Override
        public String toString() {
            return ToString.create(PROVIDER_NAME);
        }

        @Override
        public AwsCredentials resolveCredentials() {
            if(StringUtils.isEmpty(accessKeyId)) {
                throw SdkClientException.builder().message(String.format(
                        "Unable to load credentials from system settings. Access key must be specified either via environment variable (%s) or system property (%s).",
                        SdkSystemSetting.AWS_ACCESS_KEY_ID.environmentVariable(), SdkSystemSetting.AWS_ACCESS_KEY_ID.property())).build();
            } else if(StringUtils.isEmpty(secretKey)) {
                throw SdkClientException.builder().message(String.format(
                        "Unable to load credentials from system settings. Secret key must be specified either via environment variable (%s) or system property (%s).",
                        SdkSystemSetting.AWS_SECRET_ACCESS_KEY.environmentVariable(), SdkSystemSetting.AWS_SECRET_ACCESS_KEY.property())).build();
            } else {
                return AwsBasicCredentials.create(accessKeyId, secretKey);
            }
        }
    }

}
