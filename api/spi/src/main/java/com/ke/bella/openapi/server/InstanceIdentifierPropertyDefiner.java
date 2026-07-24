package com.ke.bella.openapi.server;

import ch.qos.logback.core.PropertyDefinerBase;
import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;

import java.util.UUID;

/**
 * Logback PropertyDefiner - 在 Logback 初始化时动态获取实例标识符
 * 使用方式：在 logback-spring.xml 中定义
 * <define name="INSTANCE_IDENTIFIER" class="com.ke.bella.openapi.server.InstanceIdentifierPropertyDefiner"/>
 */
public class InstanceIdentifierPropertyDefiner extends PropertyDefinerBase {

    private static volatile String cachedIdentifier;

    @Override
    public String getPropertyValue() {
        // 双重检查锁定，确保线程安全且只生成一次
        if (cachedIdentifier == null) {
            synchronized (InstanceIdentifierPropertyDefiner.class) {
                if (cachedIdentifier == null) {
                    cachedIdentifier = generateIdentifier();
                }
            }
        }
        return cachedIdentifier;
    }

    private String generateIdentifier() {
        // 在最开始生成随机字符串作为备用
        String randomId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        InetUtils inetUtils = null;
        try {
            inetUtils = new InetUtils(new InetUtilsProperties());
            String ip = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();

            // 使用 IP + 随机字符串确保唯一性
            String identifier = String.format("%s-%s", ip, randomId);

            // 同时设置系统属性，供其他地方使用
            System.setProperty("bella.instance.identifier", identifier);

            return identifier;
        } catch (Exception e) {
            // Logback 初始化阶段不能使用日志，只能输出到标准错误
            System.err.println("Failed to get instance identifier: " + e.getMessage() + ", using random id: " + randomId);
            return randomId;
        } finally {
            if (inetUtils != null) {
                inetUtils.close();
            }
        }
    }
}
