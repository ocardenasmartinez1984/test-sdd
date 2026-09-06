package com.auth.infrastructure.security;

import com.auth.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Servicio de infraestructura que gestiona los tokens JWT del auth-service:
 * generación, validación y extracción del nombre de usuario.
 *
 * <p>Firma los tokens con HMAC-SHA usando el secreto configurado
 * ({@code jwt.secret}) y aplica el tiempo de expiración configurado
 * ({@code jwt.expiration}). Incluye en el token los claims de identidad, roles
 * y permisos del usuario.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    /**
     * Genera un token JWT firmado para el usuario dado, incluyendo como claims
     * su id, email, y los roles y permisos aplanados en cadenas separadas por
     * comas, con fechas de emisión y expiración.
     *
     * @param user usuario para el que se emite el token
     * @return el token JWT compacto y firmado
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        String roles = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.joining(","));

        String permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getName())
                .collect(Collectors.joining(","));

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Valida un token JWT verificando su firma y su estructura.
     *
     * @param token token JWT a verificar
     * @return {@code true} si el token es válido; {@code false} si la firma es
     *         inválida, está mal formado o ha expirado (cualquier excepción)
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extrae el nombre de usuario (subject) contenido en un token JWT válido.
     *
     * @param token token JWT firmado y válido
     * @return el subject (nombre de usuario) del token
     * @throws io.jsonwebtoken.JwtException si el token es inválido, está mal
     *                                      formado o ha expirado
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * Deriva la clave secreta de firma HMAC a partir del secreto configurado.
     *
     * @return la clave simétrica utilizada para firmar y verificar tokens
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
