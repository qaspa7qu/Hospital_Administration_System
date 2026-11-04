package com.smart.schedule.dto.schedule;

import com.smart.schedule.entity.WorkingSchedule;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record WorkingScheduleResponse(
        Long id,
        Long doctorId,
        String doctorFullName,
        DayOfWeek dayOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        int slotDurationMinutes,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static WorkingScheduleResponse from(WorkingSchedule schedule) {
        return new WorkingScheduleResponse(
                schedule.getId(),
                schedule.getDoctor().getId(),
                schedule.getDoctor().getFullName(),
                schedule.getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getSlotDurationMinutes(),
                schedule.isActive(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt());
    }
}
