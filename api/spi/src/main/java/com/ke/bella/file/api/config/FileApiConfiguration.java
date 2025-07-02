package com.ke.bella.file.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FileApiConfiguration {
    @Bean
    @ConfigurationProperties(value = "bella.file-api")
    public FileApiProperties fileApiProperties() {
        return new FileApiProperties();
    }
}
