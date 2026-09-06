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

/**
 * Configuración de indicadores de salud (health checks) reactivos.
 *
 * <p>Pertenece a la capa de infraestructura y expone, a través de Spring Boot
 * Actuator, el estado de conectividad de las dependencias externas del servicio:
 * MongoDB y el broker de Kafka. Cada indicador aplica timeouts para evitar
 * bloqueos y degrada a estado {@code DOWN} ante errores.</p>
 */
@Configuration
@RequiredArgsConstructor
public class HealthCheckConfig {

    private final ReactiveMongoTemplate mongoTemplate;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String kafkaBootstrapServers;

    /**
     * Indicador de salud reactivo para MongoDB.
     *
     * <p>Ejecuta el comando {@code {ping: 1}} contra la base de datos; devuelve
     * {@code UP} con detalles de conexión si responde, o {@code DOWN} con el
     * error o un timeout de 3 segundos si falla.</p>
     *
     * @return indicador de salud reactivo de MongoDB
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
     * Indicador de salud reactivo para el broker de Kafka.
     *
     * <p>Crea un {@code AdminClient} con timeouts cortos y solicita el listado de
     * tópicos para verificar la conectividad; devuelve {@code UP} si el broker
     * responde o {@code DOWN} con el error o un timeout de 5 segundos si falla.</p>
     *
     * @return indicador de salud reactivo de Kafka
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
