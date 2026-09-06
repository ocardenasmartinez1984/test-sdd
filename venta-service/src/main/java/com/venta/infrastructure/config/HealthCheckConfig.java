package com.venta.infrastructure.config;

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
 * Configuración de indicadores de salud (Actuator) para las dependencias de
 * infraestructura del venta-service.
 *
 * <p>Expone health indicators reactivos para MongoDB y Kafka con timeouts, de
 * modo que {@code /actuator/health} refleje la conectividad real a esas
 * dependencias sin quedarse bloqueado.
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
     * <p>Ejecuta un {@code ping} contra la base de datos; devuelve UP si responde
     * y DOWN ante error o timeout de 3 segundos.
     *
     * @return indicador reactivo de salud de MongoDB
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
     * <p>Crea un {@code AdminClient} y lista los tópicos para comprobar la
     * conectividad con el broker; devuelve UP si responde y DOWN ante error o
     * timeout de 5 segundos.
     *
     * @return indicador reactivo de salud de Kafka
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
