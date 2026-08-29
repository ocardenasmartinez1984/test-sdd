package com.auth.infrastructure.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fails fast at startup if the JWT secret is the documented placeholder or
 * shorter than 32 bytes (256 bits, the minimum for HS256).
 *
 * <p>Without this guard, the application would happily boot with the default
 * placeholder baked into {@code application.yml} and produce tokens that
 * anyone with the source code can forge.
 */
@Configuration
public class JwtSecretValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);

    private static final String PLACEHOLDER = "<change_me_at_least_256_bits_long>";
    private static final int MIN_SECRET_BYTES = 32;

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

    private static void fail(String reason) {
        throw new IllegalStateException(
                "Refusing to start auth-service: " + reason
                        + " See .env.example for guidance.");
    }
}
