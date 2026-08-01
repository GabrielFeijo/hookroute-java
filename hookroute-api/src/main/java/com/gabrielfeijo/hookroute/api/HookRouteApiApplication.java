package com.gabrielfeijo.hookroute.api;

import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.gabrielfeijo.hookroute.api", "com.gabrielfeijo.hookroute.infrastructure"})
@EntityScan(basePackageClasses = WebhookEventEntity.class)
@EnableJpaRepositories(basePackageClasses = WebhookEventRepository.class)
public class HookRouteApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(HookRouteApiApplication.class, args);
    }
}
