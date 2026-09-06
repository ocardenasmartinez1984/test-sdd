package com.auth.application;

import com.auth.application.dto.CreateUserRequest;
import com.auth.application.dto.UpdateUserRequest;
import com.auth.application.dto.UserDto;
import com.auth.domain.model.Role;
import com.auth.domain.model.User;
import com.auth.domain.repository.RoleRepository;
import com.auth.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de aplicación que implementa el CRUD administrativo de usuarios del
 * auth-service (listar, consultar, crear, actualizar y eliminar).
 *
 * <p>Pertenece a la capa de aplicación (DDD). Colabora con
 * {@link UserRepository} y {@link RoleRepository} para el acceso a datos y con
 * {@link PasswordEncoder} para el hasheo de contraseñas. Todos los métodos se
 * ejecutan dentro de una transacción ({@code @Transactional} a nivel de clase).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Recupera todos los usuarios y los transforma en DTOs de solo lectura.
     *
     * @return la lista de usuarios como {@link UserDto}
     */
    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    /**
     * Busca un usuario por su identificador y lo transforma en DTO.
     *
     * @param id identificador del usuario
     * @return el usuario encontrado como {@link UserDto}
     * @throws RuntimeException si no existe un usuario con ese identificador
     */
    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return mapToDto(user);
    }

    /**
     * Crea un usuario nuevo con la contraseña hasheada y los roles resueltos a
     * partir de sus nombres, y lo persiste.
     *
     * <p>Efectos secundarios: guarda un nuevo {@code User} en la base de datos.
     *
     * @param request datos del usuario a crear (incluye los nombres de rol)
     * @return el usuario creado como {@link UserDto}
     * @throws RuntimeException si alguno de los roles indicados no existe
     */
    public UserDto create(CreateUserRequest request) {
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .roles(resolveRoles(request.getRoles()))
                .build();

        User savedUser = userRepository.save(user);
        return mapToDto(savedUser);
    }

    /**
     * Actualiza un usuario existente. Modifica email y nombre completo; y de
     * forma opcional el estado habilitado, la contraseña (rehasheándola) y los
     * roles, solo cuando dichos campos vienen informados en la petición.
     *
     * <p>Efectos secundarios: persiste los cambios del {@code User} en la base
     * de datos.
     *
     * @param id      identificador del usuario a actualizar
     * @param request datos a modificar (los campos nulos/vacíos se ignoran)
     * @return el usuario actualizado como {@link UserDto}
     * @throws RuntimeException si el usuario no existe o si alguno de los roles
     *                          indicados no existe
     */
    public UserDto update(Long id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());

        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getRoles() != null) {
            user.setRoles(resolveRoles(request.getRoles()));
        }

        User updatedUser = userRepository.save(user);
        return mapToDto(updatedUser);
    }

    /**
     * Elimina un usuario por su identificador.
     *
     * <p>Efectos secundarios: borra el {@code User} correspondiente de la base
     * de datos.
     *
     * @param id identificador del usuario a eliminar
     */
    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Resuelve un conjunto de nombres de rol a sus entidades {@link Role}
     * correspondientes.
     *
     * @param roleNames nombres de rol a resolver; puede ser nulo o vacío
     * @return el conjunto de roles resueltos; vacío si no se indicó ninguno
     * @throws RuntimeException si alguno de los nombres no corresponde a un rol
     *                          existente
     */
    private Set<Role> resolveRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return new HashSet<>();
        }
        return roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + name)))
                .collect(Collectors.toSet());
    }

    /**
     * Convierte una entidad {@link User} en su representación de transporte
     * {@link UserDto}, aplanando los roles a sus nombres.
     *
     * @param user entidad de usuario a mapear
     * @return el DTO equivalente
     */
    private UserDto mapToDto(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .roles(roleNames)
                .build();
    }
}
