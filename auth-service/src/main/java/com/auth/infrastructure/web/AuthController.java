package com.auth.infrastructure.web;

import com.auth.application.AuthApplicationService;
import com.auth.application.dto.AuthResponse;
import com.auth.application.dto.LoginRequest;
import com.auth.application.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST que expone los endpoints de autenticación bajo
 * {@code /api/v1/auth}: inicio de sesión, registro y validación de tokens.
 *
 * <p>Pertenece a la capa de infraestructura (adaptador web). Delega toda la
 * lógica en {@link AuthApplicationService} y traduce las excepciones de negocio
 * a códigos HTTP apropiados.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authService;

    /**
     * Autentica al usuario con sus credenciales.
     *
     * @param request credenciales de acceso (usuario y contraseña)
     * @return HTTP 200 con la respuesta de autenticación (token y datos)
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Registra un usuario nuevo.
     *
     * @param request datos de alta del usuario
     * @return HTTP 201 con la respuesta de autenticación del usuario creado
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Valida un token JWT recibido como parámetro de consulta.
     *
     * @param token token JWT a verificar
     * @return HTTP 200 con {@code true} si el token es válido, {@code false} en
     *         caso contrario
     */
    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateToken(@RequestParam String token) {
        return ResponseEntity.ok(authService.validateToken(token));
    }

    /**
     * Manejador de excepciones que traduce las {@link RuntimeException} de
     * negocio a respuestas HTTP: 401 para credenciales inválidas o cuenta
     * deshabilitada, 409 para conflictos de duplicados, 404 para recursos no
     * encontrados y 500 en el resto de casos.
     *
     * @param ex la excepción capturada
     * @return la respuesta HTTP con el código y el mensaje de error
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        if (message != null) {
            if (message.contains("Invalid credentials") || message.contains("disabled")) {
                status = HttpStatus.UNAUTHORIZED;
            } else if (message.contains("already exists")) {
                status = HttpStatus.CONFLICT;
            } else if (message.contains("not found")) {
                status = HttpStatus.NOT_FOUND;
            }
        }

        return ResponseEntity.status(status).body(Map.of("error", message != null ? message : "Unknown error"));
    }
}
