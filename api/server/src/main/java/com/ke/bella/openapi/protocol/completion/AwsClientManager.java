package com.ke.bella.openapi.protocol.completion;

import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.utils.AttributeMap;
import software.amazon.awssdk.utils.ToString;

public class AwsClientManager {

    private static final Map<String, ConcurrentHashMap<String, BedrockRuntimeClient>> httpCache = new ConcurrentHashMap<>();

    private static final Map<String, ConcurrentHashMap<String, BedrockRuntimeAsyncClient>> asyncCache = new ConcurrentHashMap<>();

    private static final Map<String, AwsAuthorizationProvider> authCache = new ConcurrentHashMap<>();

    public static BedrockRuntimeClient client(String region, String endpoint, String accessKeyId, String secretKey) {
        return httpCache.computeIfAbsent(region, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(accessKeyId, k -> BedrockRuntimeClient.builder()
                        .endpointOverride(URI.create(endpoint))
                        .credentialsProvider(provide(accessKeyId, secretKey))
                        .region(Region.of(region))
                        .httpClient(ApacheHttpClient.builder()
                                .buildWithDefaults(AttributeMap.builder()
                                        .put(SdkHttpConfigurationOption.PROTOCOL, Protocol.HTTP1_1)
                                        .build()))
                        .overrideConfiguration(ClientOverrideConfiguration.builder()
                                .apiCallTimeout(Duration.of(180, ChronoUnit.SECONDS))
                                .build())
                        .build());
    }

    public static BedrockRuntimeAsyncClient asyncClient(String region, String endpoint, String accessKeyId, String secretKey) {
        return asyncCache.computeIfAbsent(region, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(accessKeyId, k -> BedrockRuntimeAsyncClient.builder()
                        .endpointOverride(URI.create(endpoint))
                        .credentialsProvider(provide(accessKeyId, secretKey))
                        .region(Region.of(region))
                        .httpClient(NettyNioAsyncHttpClient.builder()
                                .buildWithDefaults(AttributeMap.builder()
                                        .put(SdkHttpConfigurationOption.PROTOCOL, Protocol.HTTP1_1)
                                        .build()))
                        .overrideConfiguration(ClientOverrideConfiguration.builder()
                                .apiCallTimeout(Duration.of(180, ChronoUnit.SECONDS))
                                .build())
                        .build());
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
