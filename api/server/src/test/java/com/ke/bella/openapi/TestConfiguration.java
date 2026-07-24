package com.ke.bella.openapi;

import com.alicp.jetcache.anno.config.EnableMethodCache;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = { "com.ke.bella.openapi" })
@EnableConfigurationProperties
@EnableMethodCache(basePackages = "com.ke.bella.openapi")
public class TestConfiguration {

}
