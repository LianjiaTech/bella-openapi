package com.ke.bella.openapi.server;

import java.util.Optional;

import org.springframework.cloud.commons.util.InetUtils;
import org.springframework.cloud.commons.util.InetUtilsProperties;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;

@Slf4j
public class BellaServerContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    private final InetUtils inetUtils = new InetUtils(new InetUtilsProperties());

    @Override
    public void initialize(@NotNull ConfigurableApplicationContext configurableApplicationContext) {
        Environment environment = configurableApplicationContext.getEnvironment();

        // 检查是否启用初始化器
        String enabled = environment.getProperty("bella.server.initializer.enabled");
        if(!Boolean.parseBoolean(enabled)) {
            return;
        }

        try {
            String ip = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
            Integer port = Optional.ofNullable(environment.getProperty("server.port")).map(Integer::valueOf).orElse(8080);
            String applicationName = environment.getProperty("spring.application.name");

            BellaServerContext context = BellaServerContext.builder()
                    .ip(ip)
                    .port(port)
                    .applicationName(applicationName)
                    .build();

            BellaServerContextHolder.setContext(context);
            log.info("BellaServerContextInitializer initialize() => ip={}, port={}, applicationName={}",
                    ip, port, applicationName);
        } catch (Exception e) {
            log.warn("BellaServerContextInitializer initialize() failed, e: ", e);
        } finally {
            inetUtils.close();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10000;
    }
}
