package com.smart.schedule.dto.user;

import com.smart.schedule.entity.enums.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 100) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 255) String fullName,
        @Email @Size(max = 150) String email,
        @Size(max = 30) String phone,
        Boolean enabled,
        @NotEmpty Set<RoleName> roles
) {
}
