package com.auth.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO de salida que representa a un usuario en las respuestas de la API de
 * gestión de usuarios, exponiendo únicamente datos seguros (sin contraseña) y
 * los nombres de sus roles.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private boolean enabled;
    private LocalDateTime createdAt;
    private Set<String> roles;
}
