package com.ke.bella.openapi.protocol.completion;

import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.Protocol;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.ToString;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AwsClientManager {

    private static final Duration BEDROCK_RUNTIME_TIMEOUT = Duration.ofSeconds(600);

    private static final Map<String, ConcurrentHashMap<String, BedrockRuntimeClient>> httpCache = new ConcurrentHashMap<>();

    private static final Map<String, ConcurrentHashMap<String, BedrockRuntimeAsyncClient>> asyncCache = new ConcurrentHashMap<>();

    private static final Map<String, AwsAuthorizationProvider> authCache = new ConcurrentHashMap<>();

    public static BedrockRuntimeClient client(String region, String endpoint, String accessKeyId, String secretKey) {
        return client(region, endpoint, accessKeyId, secretKey, null);
    }

    public static BedrockRuntimeClient client(String region, String endpoint, String accessKeyId, String secretKey, String proxyUrl) {
        String cacheKey = StringUtils.isNotEmpty(proxyUrl) ? accessKeyId + "|" + proxyUrl : accessKeyId;
        return httpCache.computeIfAbsent(region, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(cacheKey, k -> {
                    ApacheHttpClient.Builder httpClientBuilder = ApacheHttpClient.builder();
                    if(StringUtils.isNotEmpty(proxyUrl)) {
                        URI proxyUri = URI.create(proxyUrl);
                        ProxyConfiguration.Builder proxyBuilder = ProxyConfiguration.builder()
                                .endpoint(URI.create(proxyUri.getScheme() + "://" + proxyUri.getHost() + ":" + proxyUri.getPort()));
                        if(proxyUri.getUserInfo() != null) {
                            String[] userInfo = proxyUri.getUserInfo().split(":", 2);
                            proxyBuilder.username(userInfo[0]);
                            if(userInfo.length > 1) {
                                proxyBuilder.password(userInfo[1]);
                            }
                        }
                        httpClientBuilder.proxyConfiguration(proxyBuilder.build());
                    }
                    return BedrockRuntimeClient.builder()
                            .endpointOverride(URI.create(endpoint))
                            .credentialsProvider(provide(accessKeyId, secretKey))
                            .region(Region.of(region))
                            .httpClient(httpClientBuilder
                                    .buildWithDefaults(AttributeMap.builder()
                                            .put(SdkHttpConfigurationOption.PROTOCOL, Protocol.HTTP1_1)
                                            .put(SdkHttpConfigurationOption.READ_TIMEOUT, BEDROCK_RUNTIME_TIMEOUT)
                                            .build()))
                            .overrideConfiguration(ClientOverrideConfiguration.builder()
                                    .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                                    .apiCallTimeout(BEDROCK_RUNTIME_TIMEOUT)
                                    .apiCallAttemptTimeout(BEDROCK_RUNTIME_TIMEOUT)
                                    .build())
                            .build();
                });
    }

    public static BedrockRuntimeAsyncClient asyncClient(String region, String endpoint, String accessKeyId, String secretKey) {
        return asyncClient(region, endpoint, accessKeyId, secretKey, null);
    }

    public static BedrockRuntimeAsyncClient asyncClient(String region, String endpoint, String accessKeyId, String secretKey, String proxyUrl) {
        String cacheKey = StringUtils.isNotEmpty(proxyUrl) ? accessKeyId + "|" + proxyUrl : accessKeyId;
        return asyncCache.computeIfAbsent(region, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(cacheKey, k -> {
                    NettyNioAsyncHttpClient.Builder httpClientBuilder = NettyNioAsyncHttpClient.builder();
                    if(StringUtils.isNotEmpty(proxyUrl)) {
                        URI proxyUri = URI.create(proxyUrl);
                        httpClientBuilder.proxyConfiguration(
                                software.amazon.awssdk.http.nio.netty.ProxyConfiguration.builder()
                                        .scheme(proxyUri.getScheme())
                                        .host(proxyUri.getHost())
                                        .port(proxyUri.getPort())
                                        .build());
                    }
                    return BedrockRuntimeAsyncClient.builder()
                            .endpointOverride(URI.create(endpoint))
                            .credentialsProvider(provide(accessKeyId, secretKey))
                            .region(Region.of(region))
                            .httpClient(httpClientBuilder
                                    .buildWithDefaults(AttributeMap.builder()
                                            .put(SdkHttpConfigurationOption.PROTOCOL, Protocol.HTTP1_1)
                                            .put(SdkHttpConfigurationOption.READ_TIMEOUT, BEDROCK_RUNTIME_TIMEOUT)
                                            .build()))
                            .overrideConfiguration(ClientOverrideConfiguration.builder()
                                    .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                                    .build())
                            .build();
                });
    }

    private static AwsAuthorizationProvider provide(String accessKeyId, String secretKey) {
        return authCache.computeIfAbsent(accessKeyId, k -> new AwsAuthorizationProvider(accessKeyId, secretKey));
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
