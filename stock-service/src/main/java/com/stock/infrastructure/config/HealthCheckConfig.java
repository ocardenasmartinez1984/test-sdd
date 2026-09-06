package com.stock.infrastructure.config;

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

/**
 * Configuración de indicadores de salud reactivos (capa de infraestructura).
 *
 * <p>Expone <i>health indicators</i> personalizados para MongoDB y Kafka que
 * Spring Boot Actuator integra en el endpoint {@code /actuator/health},
 * permitiendo verificar la conectividad con ambas dependencias con timeouts
 * acotados.</p>
 */
@Configuration
@RequiredArgsConstructor
public class HealthCheckConfig {

    private final ReactiveMongoTemplate mongoTemplate;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;

    /**
     * Indicador de salud de MongoDB.
     *
     * <p>Ejecuta el comando {@code ping} contra MongoDB y reporta {@code UP} si
     * responde correctamente o {@code DOWN} ante error o timeout (3 segundos),
     * incluyendo detalles del estado en la respuesta.</p>
     *
     * @return el {@link ReactiveHealthIndicator} de MongoDB
     */
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

    /**
     * Indicador de salud de Kafka.
     *
     * <p>Crea un {@link AdminClient} temporal contra los brokers configurados e
     * intenta listar los topics; reporta {@code UP} si lo consigue o {@code DOWN}
     * ante error o timeout (5 segundos), incluyendo detalles del broker en la
     * respuesta.</p>
     *
     * @return el {@link ReactiveHealthIndicator} de Kafka
     */
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
