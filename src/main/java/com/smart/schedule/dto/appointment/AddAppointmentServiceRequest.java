package com.smart.schedule.dto.appointment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddAppointmentServiceRequest(
        @NotNull Long serviceId,
        @Min(1) @Max(100) Integer quantity
) {
}
