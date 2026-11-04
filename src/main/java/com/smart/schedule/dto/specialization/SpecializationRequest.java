package com.smart.schedule.dto.specialization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecializationRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description
) {
}
