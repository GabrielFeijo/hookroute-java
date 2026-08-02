package com.gabrielfeijo.hookroute.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI hookRouteOpenApi() {
        return new OpenAPI()
                .components(new Components().addSecuritySchemes("admin-api-key", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("x-admin-api-key")
                        .description("Administrative API key.")))
                .info(new Info()
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
