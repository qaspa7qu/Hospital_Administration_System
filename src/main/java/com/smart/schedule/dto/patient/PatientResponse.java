package com.smart.schedule.dto.patient;

import com.smart.schedule.entity.Patient;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record PatientResponse(
        Long id,
        String firstName,
        String lastName,
        String middleName,
        LocalDate birthDate,
        String phone,
        String email,
        String address,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getMiddleName(),
                patient.getBirthDate(),
                patient.getPhone(),
                patient.getEmail(),
                patient.getAddress(),
                patient.getNotes(),
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }
}
