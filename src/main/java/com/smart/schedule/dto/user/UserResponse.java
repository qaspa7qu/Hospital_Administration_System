package com.smart.schedule.dto.user;

import com.smart.schedule.entity.User;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        boolean enabled,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse from(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.isEnabled(),
                roles,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
