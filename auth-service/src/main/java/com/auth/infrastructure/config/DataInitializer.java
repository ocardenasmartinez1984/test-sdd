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

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
