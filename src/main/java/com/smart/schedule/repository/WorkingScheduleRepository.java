package com.smart.schedule.repository;

import com.smart.schedule.entity.WorkingSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public interface WorkingScheduleRepository extends JpaRepository<WorkingSchedule, Long> {

    List<WorkingSchedule> findByDoctorIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(Long doctorId);

    List<WorkingSchedule> findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
            Long doctorId, DayOfWeek dayOfWeek);

    List<WorkingSchedule> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);

    boolean existsByDoctorIdAndDayOfWeekAndStartTime(
            Long doctorId, DayOfWeek dayOfWeek, LocalTime startTime);

    boolean existsByDoctorIdAndDayOfWeekAndStartTimeAndIdNot(
            Long doctorId, DayOfWeek dayOfWeek, LocalTime startTime, Long id);

    void deleteByDoctorId(Long doctorId);
}
