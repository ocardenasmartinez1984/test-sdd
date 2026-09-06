package com.auth.domain.repository;

import com.auth.domain.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos para la entidad {@link Role}.
 *
 * <p>Pertenece a la capa de dominio (puerto de persistencia). Extiende
 * {@link JpaRepository} para las operaciones CRUD estándar y añade una consulta
 * derivada por nombre de rol.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * Busca un rol por su nombre.
     *
     * @param name nombre del rol a buscar (p. ej. {@code ROLE_ADMIN})
     * @return el rol envuelto en {@link Optional}, vacío si no existe
     */
    Optional<Role> findByName(String name);
}
