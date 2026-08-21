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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validateToken(@RequestParam String token) {
        return ResponseEntity.ok(authService.validateToken(token));
    }

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
