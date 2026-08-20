package com.despacho.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class HealthCheckConfig {

    private final ReactiveMongoTemplate mongoTemplate;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;

    @Bean
    public ReactiveHealthIndicator mongoHealthIndicator() {
        return () -> mongoTemplate.executeCommand("{ping: 1}")
                .map(result -> Health.up()
                        .withDetail("database", "MongoDB")
                        .withDetail("status", "connected")
                        .build())
                .onErrorResume(ex -> Mono.just(Health.down()
                        .withDetail("database", "MongoDB")
                        .withDetail("error", ex.getMessage())
                        .build()))
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(ex -> Mono.just(Health.down()
                        .withDetail("database", "MongoDB")
                        .withDetail("error", "Timeout")
                        .build()));
    }

    @Bean
    public ReactiveHealthIndicator kafkaHealthIndicator() {
        return () -> Mono.fromCallable(() -> {
            try (AdminClient client = AdminClient.create(
                    Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers,
                           AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
                           AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 3000))) {
                client.listTopics().names().get();
                return Health.up()
                        .withDetail("broker", "Kafka")
                        .withDetail("status", "connected")
                        .build();
            } catch (Exception e) {
                return Health.down()
                        .withDetail("broker", "Kafka")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        }).timeout(Duration.ofSeconds(5))
          .onErrorResume(ex -> Mono.just(Health.down()
                  .withDetail("broker", "Kafka")
                  .withDetail("error", "Timeout")
                  .build()));
    }
}
