package com.gabrielfeijo.hookroute.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI hookRouteOpenApi() {
        return new OpenAPI().info(new Info()
                .title("HookRoute Java Backend")
                .description("API para ingestão, auditoria e processamento assíncrono de webhooks")
                .version("1.0"));
    }

    @Bean
    WebMvcConfigurer corsConfiguration() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**").allowedOriginPatterns("*").allowedMethods("*").allowedHeaders("*");
            }
        };
    }
}
