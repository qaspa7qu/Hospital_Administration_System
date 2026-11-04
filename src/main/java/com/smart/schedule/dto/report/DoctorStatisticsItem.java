package com.smart.schedule.dto.report;

import java.math.BigDecimal;

public record DoctorStatisticsItem(
        Long doctorId,
        String doctorFullName,
        String specializationName,
        long totalAppointments,
        long scheduledAppointments,
        long completedAppointments,
        long cancelledAppointments,
        BigDecimal completedRevenue
) {
}
