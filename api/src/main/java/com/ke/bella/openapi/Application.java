package com.ke.bella.openapi;

import com.ke.bella.openapi.configuration.OpenapiBeanNameGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;

/**
 * 服务启动类
 *
 * @author keboot
 */
@EnableApolloConfig
@ComponentScan(basePackages = { "com.ke.bella.openapi" }, nameGenerator = OpenapiBeanNameGenerator.class)
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
