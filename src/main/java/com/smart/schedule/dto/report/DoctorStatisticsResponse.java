package com.smart.schedule.dto.report;

import java.time.LocalDate;
import java.util.List;

public record DoctorStatisticsResponse(
        LocalDate from,
        LocalDate to,
        List<DoctorStatisticsItem> doctors
) {
}
