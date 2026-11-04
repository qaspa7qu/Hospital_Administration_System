package com.smart.schedule.dto.doctor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDoctorRequest(
        @NotNull Long userId,
        @NotNull Long specializationId,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String middleName,
        @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 30) String officeNumber,
        @Size(max = 1000) String bio,
        Boolean active
) {
}
