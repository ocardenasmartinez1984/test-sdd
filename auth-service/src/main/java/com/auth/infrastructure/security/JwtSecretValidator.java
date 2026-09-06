package com.auth.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Validador que falla de forma temprana en el arranque si el secreto JWT es el
 * marcador de posición documentado o si es más corto de 32 bytes (256 bits, el
 * mínimo para HS256).
 *
 * <p>Sin esta protección, la aplicación arrancaría con el marcador de posición
 * por defecto incluido en {@code application.yml} y produciría tokens que
 * cualquiera con acceso al código fuente podría falsificar. Pertenece a la capa
 * de infraestructura de seguridad.
 */
@Configuration
public class JwtSecretValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);

    private static final String PLACEHOLDER = "<change_me_at_least_256_bits_long>";
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Crea un {@link ApplicationRunner} que valida el secreto JWT al iniciar la
     * aplicación: comprueba que esté presente, que no sea el marcador de
     * posición y que tenga la longitud mínima requerida por HS256.
     *
     * @param secret el secreto JWT configurado ({@code jwt.secret}); vacío si no
     *               se ha establecido
     * @return el runner de arranque que ejecuta la validación
     * @throws IllegalStateException (dentro del runner) si el secreto está
     *                               ausente, es el marcador de posición o es
     *                               demasiado corto
     */
    @Bean
    public ApplicationRunner validateJwtSecret(
            @Value("${jwt.secret:}") String secret) {
        return args -> {
            if (secret == null || secret.isBlank()) {
                fail("JWT secret is not set. Set the JWT_SECRET environment variable.");
            }
            if (PLACEHOLDER.equals(secret)) {
                fail("JWT secret is the documented placeholder. Replace it with a real "
                        + "value of at least " + MIN_SECRET_BYTES + " bytes.");
            }
            int bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes < MIN_SECRET_BYTES) {
                fail("JWT secret is too short: " + bytes + " bytes. HS256 requires at least "
                        + MIN_SECRET_BYTES + " bytes (256 bits).");
            }
            log.info("JWT secret validated: {} bytes (>= {} required)", bytes, MIN_SECRET_BYTES);
        };
    }

    /**
     * Aborta el arranque del servicio lanzando una excepción con el motivo
     * indicado.
     *
     * @param reason descripción del problema de configuración detectado
     * @throws IllegalStateException siempre, para impedir que el servicio inicie
     */
    private static void fail(String reason) {
        throw new IllegalStateException(
                "Refusing to start auth-service: " + reason
                        + " See .env.example for guidance.");
    }
}
