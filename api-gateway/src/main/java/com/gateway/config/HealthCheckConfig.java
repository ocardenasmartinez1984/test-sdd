package com.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class HealthCheckConfig {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Bean
    public ReactiveHealthIndicator redisHealthIndicator() {
        return () -> redisTemplate.getConnectionFactory()
                .getReactiveConnection()
                .ping()
                .map(response -> Health.up()
                        .withDetail("cache", "Redis")
                        .withDetail("status", "connected")
                        .build())
                .onErrorResume(ex -> Mono.just(Health.down()
                        .withDetail("cache", "Redis")
                        .withDetail("error", ex.getMessage())
                        .build()))
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(ex -> Mono.just(Health.down()
                        .withDetail("cache", "Redis")
                        .withDetail("error", "Timeout")
                        .build()));
    }
}
