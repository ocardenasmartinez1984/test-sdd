package com.auth.infrastructure.config;

import com.auth.domain.model.Role;
import com.auth.domain.model.User;
import com.auth.domain.repository.RoleRepository;
import com.auth.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Inicializador de datos que se ejecuta al arrancar la aplicación para
 * garantizar un estado mínimo del sistema de autenticación.
 *
 * <p>Pertenece a la capa de infraestructura (configuración). Crea los roles
 * base ({@code ROLE_ADMIN}, {@code ROLE_USER}) si no existen y un usuario
 * administrador por defecto la primera vez. Colabora con
 * {@link RoleRepository}, {@link UserRepository} y {@link PasswordEncoder}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Siembra los datos iniciales: asegura la existencia de los roles
     * {@code ROLE_ADMIN} y {@code ROLE_USER} y, si no existe el usuario
     * {@code admin}, crea la cuenta administradora por defecto.
     *
     * <p>Efectos secundarios: persiste roles y el usuario administrador en la
     * base de datos, y registra en el log la creación de la cuenta admin.
     *
     * @param args argumentos de arranque de la aplicación (no utilizados)
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name("ROLE_ADMIN").description("Administrator").build()));

        roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name("ROLE_USER").description("Standard User").build()));

        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .email("admin@pos.local")
                    .password(passwordEncoder.encode("admin123"))
                    .fullName("Administrador")
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .roles(Set.of(adminRole))
                    .build();
            userRepository.save(admin);
            log.info("Admin user created: admin / admin123");
        }
    }
}
