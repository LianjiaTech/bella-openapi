package com.ke.bella.openapi.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.GroupedOpenApi;
import org.springdoc.core.customizers.OpenApiCustomiser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiDocConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Bella-Openapi").version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("Authorization", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")));
    }

    @Bean
    public GroupedOpenApi metadataConsoleApi() {
        return GroupedOpenApi.builder()
                .group("元数据管理")
                .pathsToMatch("/console/endpoint/**", "/console/model/**", "/console/channel/**", "/console/category/**")
                .addOpenApiCustomiser(customiseOpenApi())
                .build();
    }

    @Bean
    public GroupedOpenApi apiKeyConsoleApi() {
        return GroupedOpenApi.builder()
                .group("API Key管理")
                .pathsToMatch("/console/apikey/**")
                .addOpenApiCustomiser(customiseOpenApi())
                .build();
    }

    @Bean
    public GroupedOpenApi informationQueryApi() {
        return GroupedOpenApi.builder()
                .group("信息查询")
                .pathsToMatch("/v1/apikey/**", "/v1/meta/**")
                .addOpenApiCustomiser(customiseOpenApi())
                .build();
    }

    @Bean
    public GroupedOpenApi endpointApi() {
        return GroupedOpenApi.builder()
                .group("能力点")
                .pathsToMatch("/v1/**")
                .pathsToExclude("/v1/apikey/**", "/v1/meta/**")
                .addOpenApiCustomiser(customiseOpenApi())
                .build();
    }

    private OpenApiCustomiser customiseOpenApi() {
        return openApi -> openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .forEach(operation -> operation.addParametersItem(new Parameter()
                        .in("header")
                        .name("Authorization")
                        .required(true)
                        .description("Authorization header")
                        .schema(new io.swagger.v3.oas.models.media.StringSchema())));
    }
}
