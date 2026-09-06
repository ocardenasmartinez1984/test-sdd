package com.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Configuración del limitador de peticiones (rate limiting) del API Gateway.
 *
 * <p>Pertenece a la capa de infraestructura/configuración del gateway. Provee la
 * estrategia de resolución de clave que utiliza el filtro RequestRateLimiter
 * (respaldado por Redis) para agrupar y contabilizar las peticiones a limitar.</p>
 */
@Configuration
public class RateLimiterConfig {

    /**
     * Crea el {@link KeyResolver} que identifica a cada cliente por su dirección IP.
     *
     * <p>Extrae la IP remota de la petición para usarla como clave del limitador,
     * de forma que la cuota de peticiones se aplique por cliente. Si la dirección
     * remota no está disponible, devuelve la clave {@code "unknown"}.</p>
     *
     * @return un {@link KeyResolver} que emite (de forma reactiva) la IP del
     *         cliente como clave de limitación
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown"
        );
    }
}
