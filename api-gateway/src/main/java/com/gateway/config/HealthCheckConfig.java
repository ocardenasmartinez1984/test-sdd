package com.gateway.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Configuración de indicadores de salud (health checks) del API Gateway.
 *
 * <p>Pertenece a la capa de infraestructura/configuración del gateway. Amplía la
 * información de Spring Actuator añadiendo un indicador de salud propio para la
 * caché Redis, que el gateway usa para el limitado de peticiones. Colabora con el
 * {@link ReactiveRedisTemplate} inyectado para comprobar la conectividad.</p>
 */
@Configuration
@RequiredArgsConstructor
public class HealthCheckConfig {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Crea el indicador de salud reactivo que comprueba la conectividad con Redis.
     *
     * <p>Obtiene una conexión reactiva desde la fábrica del
     * {@link ReactiveRedisTemplate} y envía un comando {@code PING}. Si Redis
     * responde, reporta el estado {@code UP} con detalles de la caché; ante
     * cualquier error reporta {@code DOWN} incluyendo el mensaje de error.
     * Aplica además un tiempo máximo de espera de 3 segundos, tras el cual
     * también reporta {@code DOWN} con el detalle {@code "Timeout"}.</p>
     *
     * @return un {@link ReactiveHealthIndicator} que refleja el estado de la
     *         conexión con Redis dentro del endpoint de salud de Actuator
     */
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
