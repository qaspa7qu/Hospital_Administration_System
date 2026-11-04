package com.smart.schedule.service;

import com.smart.schedule.dto.user.CreateUserRequest;
import com.smart.schedule.dto.user.UpdateUserRequest;
import com.smart.schedule.dto.user.UserResponse;
import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.RoleRepository;
import com.smart.schedule.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            DoctorRepository doctorRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.doctorRepository = doctorRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> findAll() {
        return userRepository.findAllByOrderByUsernameAsc().stream()
                .map(UserResponse::from)
                .toList();
    }

    public UserResponse findById(Long id) {
        return UserResponse.from(findEntity(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists: " + request.username());
        }

        User user = new User(
                request.username(),
                passwordEncoder.encode(request.password()),
                request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setRoles(resolveRoles(request.roles()));

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        User user = findEntity(id);

        if (!user.getUsername().equals(request.username())
                && userRepository.existsByUsernameAndIdNot(request.username(), id)) {
            throw new ConflictException("Username already exists: " + request.username());
        }

        if (doctorRepository.existsByUserId(id)
                && !request.roles().contains(RoleName.DOCTOR)) {
            throw new ConflictException(
                    "Cannot remove DOCTOR role from a user with an existing doctor profile");
        }

        user.setUsername(request.username());
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setRoles(resolveRoles(request.roles()));

        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        return UserResponse.from(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = findEntity(id);
        if (doctorRepository.existsByUserId(id)) {
            throw new ConflictException("Cannot delete a user linked to a doctor profile");
        }
        userRepository.delete(user);
    }

    private User findEntity(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private Set<Role> resolveRoles(Set<RoleName> roleNames) {
        Set<Role> roles = new LinkedHashSet<>();
        for (RoleName roleName : roleNames) {
            roles.add(roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Role not found: " + roleName)));
        }
        return roles;
    }
}
