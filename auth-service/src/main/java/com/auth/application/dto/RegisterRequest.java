package com.auth.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO de entrada con los datos de alta de un nuevo usuario (usuario, email,
 * contraseña y nombre completo) recibidos por el endpoint de registro.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    private String username;
    private String email;
    private String password;
    private String fullName;
}
