package com.smart.schedule.dto.patient;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PatientRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String middleName,
        @Past LocalDate birthDate,
        @NotBlank @Size(max = 30) String phone,
        @Email @Size(max = 150) String email,
        @Size(max = 500) String address,
        @Size(max = 1000) String notes
) {
}
