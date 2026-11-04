package com.smart.schedule.dto.specialization;

import com.smart.schedule.entity.Specialization;

import java.time.LocalDateTime;

public record SpecializationResponse(
        Long id,
        String name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SpecializationResponse from(Specialization specialization) {
        return new SpecializationResponse(
                specialization.getId(),
                specialization.getName(),
                specialization.getDescription(),
                specialization.getCreatedAt(),
                specialization.getUpdatedAt());
    }
}
