package com.auth.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración de seguridad web (Spring Security) del auth-service.
 *
 * <p>Pertenece a la capa de infraestructura. Define la cadena de filtros de
 * seguridad, la política de sesiones sin estado (STATELESS) apropiada para una
 * API basada en JWT, la configuración CORS y el codificador de contraseñas
 * BCrypt utilizado por los servicios de aplicación.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Codificador de contraseñas basado en BCrypt, usado para hashear y
     * verificar contraseñas de usuario.
     *
     * @return la instancia de {@link PasswordEncoder} (BCrypt)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Define la cadena de filtros de seguridad HTTP: habilita CORS, deshabilita
     * CSRF/HTTP Basic/formulario de login/logout, fija la política de sesión
     * como STATELESS y permite todas las peticiones (la autorización se maneja
     * vía JWT en el gateway/servicios).
     *
     * @param http el constructor de configuración de seguridad HTTP
     * @return la cadena de filtros de seguridad construida
     * @throws Exception si ocurre un error al construir la configuración
     */
    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }

    /**
     * Configura las reglas CORS para todas las rutas ({@code /**}): permite
     * cualquier origen por patrón, los métodos HTTP habituales, todas las
     * cabeceras y credenciales.
     *
     * @return la fuente de configuración CORS registrada para toda la API
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
