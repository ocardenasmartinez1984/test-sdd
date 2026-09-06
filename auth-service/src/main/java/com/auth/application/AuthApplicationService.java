package com.auth.application;

import com.auth.application.dto.AuthResponse;
import com.auth.application.dto.LoginRequest;
import com.auth.application.dto.RegisterRequest;
import com.auth.domain.model.Role;
import com.auth.domain.model.User;
import com.auth.domain.repository.RoleRepository;
import com.auth.domain.repository.UserRepository;
import com.auth.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de aplicación que orquesta los casos de uso de autenticación del
 * auth-service (registro, inicio de sesión y validación de tokens).
 *
 * <p>Pertenece a la capa de aplicación (DDD) y coordina los colaboradores del
 * dominio e infraestructura: {@link UserRepository} y {@link RoleRepository}
 * para el acceso a datos, {@link PasswordEncoder} para el hasheo de
 * contraseñas y {@link JwtService} para la emisión/validación de tokens JWT.
 */
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registra un nuevo usuario con el rol por defecto {@code ROLE_USER},
     * persiste la entidad y emite un token JWT para la sesión recién creada.
     *
     * <p>Efectos secundarios: guarda un nuevo {@code User} en la base de datos
     * (contraseña hasheada con {@link PasswordEncoder}) y genera un token JWT.
     *
     * @param request datos de alta (usuario, email, contraseña, nombre completo)
     * @return la respuesta de autenticación con el token, datos del usuario,
     *         roles y permisos
     * @throws RuntimeException si el nombre de usuario o el email ya existen, o
     *                          si no se encuentra el rol por defecto
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Username already exists: " + request.getUsername());
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }

        Role defaultRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Default role not found"));

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .roles(new HashSet<>(Set.of(defaultRole)))
                .build();

        user = userRepository.save(user);

        String token = jwtService.generateToken(user);

        return buildAuthResponse(user, token);
    }

    /**
     * Autentica a un usuario verificando sus credenciales y su estado, y emite
     * un token JWT en caso de éxito.
     *
     * @param request credenciales de acceso (usuario y contraseña)
     * @return la respuesta de autenticación con el token, datos del usuario,
     *         roles y permisos
     * @throws RuntimeException si el usuario no existe, la contraseña no
     *                          coincide o la cuenta está deshabilitada
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new RuntimeException("User account is disabled");
        }

        String token = jwtService.generateToken(user);

        return buildAuthResponse(user, token);
    }

    /**
     * Valida un token JWT delegando en {@link JwtService}.
     *
     * @param token token JWT a verificar
     * @return {@code true} si el token es válido y no ha expirado; {@code false}
     *         en caso contrario
     */
    public boolean validateToken(String token) {
        return jwtService.validateToken(token);
    }

    /**
     * Construye la respuesta de autenticación aplanando los roles del usuario y
     * los permisos asociados a esos roles en conjuntos de nombres.
     *
     * @param user  usuario autenticado del que se extraen roles y permisos
     * @param token token JWT ya generado para el usuario
     * @return el DTO {@link AuthResponse} con token, usuario, nombre, roles y
     *         permisos
     */
    private AuthResponse buildAuthResponse(User user, String token) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(permission -> permission.getName())
                .collect(Collectors.toSet());

        return AuthResponse.builder()
                .token(token)
                .username(user.getUsername())
                .fullName(user.getFullName())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
