package com.smart.schedule.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AppointmentStatisticsResponse(
        LocalDate from,
        LocalDate to,
        long totalAppointments,
        long scheduledAppointments,
        long completedAppointments,
        long cancelledAppointments,
        BigDecimal completedRevenue
) {
}
