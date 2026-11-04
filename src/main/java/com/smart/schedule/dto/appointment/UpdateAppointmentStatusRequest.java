package com.smart.schedule.dto.appointment;

import com.smart.schedule.entity.enums.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateAppointmentStatusRequest(
        @NotNull AppointmentStatus status
) {
}
