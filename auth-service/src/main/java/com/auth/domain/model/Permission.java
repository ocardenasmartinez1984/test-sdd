package com.auth.domain.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entidad de dominio que representa un permiso granular, persistida en la tabla
 * {@code permissions}.
 *
 * <p>Es la unidad mínima de autorización dentro del contexto de autenticación;
 * los permisos se agrupan en {@link Role} para asignarse a los usuarios.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;
}
