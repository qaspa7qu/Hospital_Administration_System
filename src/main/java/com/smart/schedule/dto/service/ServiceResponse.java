package com.smart.schedule.dto.service;

import com.smart.schedule.entity.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int durationMinutes,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static ServiceResponse from(Service service) {
        return new ServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getDurationMinutes(),
                service.isActive(),
                service.getCreatedAt(),
                service.getUpdatedAt());
    }
}
