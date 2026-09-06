package com.auth.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO de entrada con los datos necesarios para crear un usuario desde la API de
 * gestión, incluyendo el conjunto de nombres de rol a asignar.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateUserRequest {

    private String username;
    private String email;
    private String password;
    private String fullName;
    private Set<String> roles;
}
