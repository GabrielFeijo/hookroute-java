package com.gabrielfeijo.hookroute.worker;

import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventEntity;
import com.gabrielfeijo.hookroute.infrastructure.persistence.WebhookEventRepository;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.gabrielfeijo.hookroute.worker", "com.gabrielfeijo.hookroute.infrastructure"})
@EntityScan(basePackageClasses = WebhookEventEntity.class)
@EnableJpaRepositories(basePackageClasses = WebhookEventRepository.class)
public class HookRouteWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(HookRouteWorkerApplication.class, args);
    }
}
