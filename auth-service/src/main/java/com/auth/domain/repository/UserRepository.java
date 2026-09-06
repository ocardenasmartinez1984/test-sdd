package com.auth.domain.repository;

import com.auth.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos para la entidad {@link User}.
 *
 * <p>Pertenece a la capa de dominio (puerto de persistencia). Extiende
 * {@link JpaRepository} para las operaciones CRUD estándar y añade consultas
 * derivadas por nombre de usuario y email.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por su nombre de usuario.
     *
     * @param username nombre de usuario a buscar
     * @return el usuario envuelto en {@link Optional}, vacío si no existe
     */
    Optional<User> findByUsername(String username);

    /**
     * Indica si existe un usuario con el nombre de usuario dado.
     *
     * @param username nombre de usuario a comprobar
     * @return {@code true} si existe; {@code false} en caso contrario
     */
    boolean existsByUsername(String username);

    /**
     * Indica si existe un usuario con el email dado.
     *
     * @param email email a comprobar
     * @return {@code true} si existe; {@code false} en caso contrario
     */
    boolean existsByEmail(String email);
}
