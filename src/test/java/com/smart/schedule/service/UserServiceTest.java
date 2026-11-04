package com.smart.schedule.service;

import com.smart.schedule.dto.user.CreateUserRequest;
import com.smart.schedule.dto.user.UpdateUserRequest;
import com.smart.schedule.dto.user.UserResponse;
import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.RoleRepository;
import com.smart.schedule.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createUserHashesPasswordAndAssignsRoles() {
        CreateUserRequest request = new CreateUserRequest(
                "new.doctor", "password123", "Новый Врач",
                "doctor@example.com", "+7 900 000-00-00", true,
                Set.of(RoleName.DOCTOR));
        Role doctorRole = new Role(RoleName.DOCTOR, "Врач");

        when(userRepository.existsByUsername("new.doctor")).thenReturn(false);
        when(roleRepository.findByName(RoleName.DOCTOR)).thenReturn(Optional.of(doctorRole));
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.create(request);

        assertEquals("new.doctor", response.username());
        assertEquals("Новый Врач", response.fullName());
        assertTrue(response.enabled());
        assertEquals(Set.of("DOCTOR"), response.roles());
        verify(passwordEncoder).encode("password123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        CreateUserRequest request = new CreateUserRequest(
                "existing.user", "password123", "Existing User",
                null, null, true, Set.of(RoleName.RECEPTIONIST));

        when(userRepository.existsByUsername("existing.user")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.create(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteUserRejectsLinkedDoctorProfile() {
        User user = new User("doctor.user", "hash", "Doctor User");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(doctorRepository.existsByUserId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.delete(1L));
        verify(userRepository, never()).delete(any(User.class));
    }

    @Test
    void updateUserRejectsRemovingDoctorRoleFromLinkedProfile() {
        User user = new User("doctor.user", "hash", "Doctor User");
        UpdateUserRequest request = new UpdateUserRequest(
                "doctor.user", null, "Doctor User",
                null, null, true, Set.of(RoleName.ADMIN));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(doctorRepository.existsByUserId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.update(1L, request));
    }

    @Test
    void updateUserKeepsPasswordWhenItIsOmitted() {
        User user = new User("doctor.user", "old-hash", "Doctor User");
        UpdateUserRequest request = new UpdateUserRequest(
                "doctor.user", null, "Updated Name",
                "new@example.com", "+7 900 111-11-11", true,
                Set.of(RoleName.DOCTOR));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(doctorRepository.existsByUserId(1L)).thenReturn(false);
        when(roleRepository.findByName(RoleName.DOCTOR))
                .thenReturn(Optional.of(new Role(RoleName.DOCTOR, "Врач")));

        userService.update(1L, request);

        assertEquals("old-hash", user.getPasswordHash());
        assertEquals("Updated Name", user.getFullName());
        verify(passwordEncoder, never()).encode(anyString());
    }
}
