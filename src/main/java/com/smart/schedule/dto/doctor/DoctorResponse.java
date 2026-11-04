package com.smart.schedule.dto.doctor;

import com.smart.schedule.entity.Doctor;

import java.time.LocalDateTime;

public record DoctorResponse(
        Long id,
        Long userId,
        String username,
        Long specializationId,
        String specializationName,
        String firstName,
        String lastName,
        String middleName,
        String fullName,
        String phone,
        String email,
        String officeNumber,
        String bio,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static DoctorResponse from(Doctor doctor) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getUser().getId(),
                doctor.getUser().getUsername(),
                doctor.getSpecialization().getId(),
                doctor.getSpecialization().getName(),
                doctor.getFirstName(),
                doctor.getLastName(),
                doctor.getMiddleName(),
                doctor.getFullName(),
                doctor.getPhone(),
                doctor.getEmail(),
                doctor.getOfficeNumber(),
                doctor.getBio(),
                doctor.isActive(),
                doctor.getCreatedAt(),
                doctor.getUpdatedAt());
    }
}
