package com.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del microservicio de autenticación (auth-service).
 *
 * <p>Arranca la aplicación Spring Boot que expone la autenticación basada en
 * JWT y la gestión de usuarios sobre PostgreSQL. Excluye la autoconfiguración
 * de seguridad reactiva porque el servicio utiliza el stack web servlet.
 */
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration.class
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
