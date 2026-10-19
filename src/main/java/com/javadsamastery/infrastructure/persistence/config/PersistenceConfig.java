package com.javadsamastery.infrastructure.persistence.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ConditionalOnProperty(name = "javadsa.persistence.enabled", havingValue = "true")
@EntityScan(basePackages = "com.javadsamastery.infrastructure.persistence.entity")
@EnableJpaRepositories(basePackages = "com.javadsamastery.infrastructure.persistence.repository")
public class PersistenceConfig {
}
