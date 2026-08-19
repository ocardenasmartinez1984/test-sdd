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

@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserDto> findAll() {
        return userRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return mapToDto(user);
    }

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

    public void delete(Long id) {
        userRepository.deleteById(id);
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return new HashSet<>();
        }
        return roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + name)))
                .collect(Collectors.toSet());
    }

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
