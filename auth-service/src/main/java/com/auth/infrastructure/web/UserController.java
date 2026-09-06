package com.auth.infrastructure.web;

import com.auth.application.UserManagementService;
import com.auth.application.dto.CreateUserRequest;
import com.auth.application.dto.UpdateUserRequest;
import com.auth.application.dto.UserDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST que expone el CRUD de usuarios bajo {@code /api/v1/users}.
 *
 * <p>Pertenece a la capa de infraestructura (adaptador web). Delega la lógica
 * en {@link UserManagementService}.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserManagementService userManagementService;

    /**
     * Lista todos los usuarios.
     *
     * @return HTTP 200 con la lista de usuarios
     */
    @GetMapping
    public ResponseEntity<List<UserDto>> findAll() {
        return ResponseEntity.ok(userManagementService.findAll());
    }

    /**
     * Obtiene un usuario por su identificador.
     *
     * @param id identificador del usuario
     * @return HTTP 200 con el usuario solicitado
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userManagementService.findById(id));
    }

    /**
     * Crea un usuario nuevo.
     *
     * @param request datos del usuario a crear
     * @return HTTP 201 con el usuario creado
     */
    @PostMapping
    public ResponseEntity<UserDto> create(@RequestBody CreateUserRequest request) {
        UserDto created = userManagementService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Actualiza un usuario existente.
     *
     * @param id      identificador del usuario a actualizar
     * @param request datos a modificar
     * @return HTTP 200 con el usuario actualizado
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDto> update(@PathVariable Long id, @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userManagementService.update(id, request));
    }

    /**
     * Elimina un usuario por su identificador.
     *
     * @param id identificador del usuario a eliminar
     * @return HTTP 204 sin contenido
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userManagementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
