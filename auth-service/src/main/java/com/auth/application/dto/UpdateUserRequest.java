package com.auth.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO de entrada con los datos actualizables de un usuario. Los campos nulos se
 * interpretan como "sin cambio", por lo que permite actualizaciones parciales.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateUserRequest {

    private String email;
    private String fullName;
    private Boolean enabled;
    private String password;
    private Set<String> roles;
}
