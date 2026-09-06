package com.auth.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO de salida devuelto tras una autenticación exitosa (login o registro).
 *
 * <p>Contiene el token JWT emitido y los datos de identidad del usuario
 * (nombre de usuario, nombre completo, roles y permisos) que necesita el
 * cliente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;
    private String username;
    private String fullName;
    private Set<String> roles;
    private Set<String> permissions;
}
