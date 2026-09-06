package com.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de CORS (Cross-Origin Resource Sharing) del API Gateway.
 *
 * <p>Pertenece a la capa de infraestructura/configuración del gateway. Define
 * la política global de intercambio de recursos entre orígenes distintos, de
 * modo que los frontends Angular (que corren en puertos diferentes al gateway)
 * puedan consumir la API sin ser bloqueados por el navegador.</p>
 */
@Configuration
public class CorsConfig {

    /**
     * Crea el filtro reactivo de CORS aplicado a todas las rutas del gateway.
     *
     * <p>Configura una política permisiva: permite cualquier origen, los métodos
     * GET, POST, PUT, DELETE y OPTIONS, cualquier cabecera, y cachea la respuesta
     * preflight durante 3600 segundos. La configuración se registra para el
     * patrón de rutas {@code /**} mediante un
     * {@link UrlBasedCorsConfigurationSource}.</p>
     *
     * @return el {@link CorsWebFilter} que aplica la política CORS a las
     *         peticiones entrantes del gateway
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
