package com.smart.schedule.service;

import com.smart.schedule.dto.schedule.WorkingScheduleRequest;
import com.smart.schedule.dto.schedule.WorkingScheduleResponse;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.UserRepository;
import com.smart.schedule.repository.WorkingScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkingScheduleServiceTest {

    @Mock
    private WorkingScheduleRepository workingScheduleRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WorkingScheduleService workingScheduleService;

    @Test
    void createScheduleSucceeds() {
        Doctor doctor = doctor(1L);
        WorkingScheduleRequest request = new WorkingScheduleRequest(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), 30, true);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository.existsByDoctorIdAndDayOfWeekAndStartTime(
                1L, DayOfWeek.MONDAY, LocalTime.of(9, 0))).thenReturn(false);
        when(workingScheduleRepository.save(any(WorkingSchedule.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WorkingScheduleResponse response = workingScheduleService.create(1L, request);

        assertEquals(1L, response.doctorId());
        assertEquals(DayOfWeek.MONDAY, response.dayOfWeek());
        assertEquals(LocalTime.of(9, 0), response.startTime());
        assertEquals(LocalTime.of(17, 0), response.endTime());
        assertEquals(30, response.slotDurationMinutes());
        verify(workingScheduleRepository).save(any(WorkingSchedule.class));
    }

    @Test
    void createScheduleRejectsInvalidTimeRange() {
        Doctor doctor = doctor(1L);
        WorkingScheduleRequest request = new WorkingScheduleRequest(
                DayOfWeek.MONDAY, LocalTime.of(17, 0), LocalTime.of(9, 0), 30, true);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));

        assertThrows(BadRequestException.class,
                () -> workingScheduleService.create(1L, request));
        verify(workingScheduleRepository, never()).save(any(WorkingSchedule.class));
    }

    @Test
    void createScheduleRejectsDuplicateDayAndStartTime() {
        Doctor doctor = doctor(1L);
        WorkingScheduleRequest request = new WorkingScheduleRequest(
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0), 30, true);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository.existsByDoctorIdAndDayOfWeekAndStartTime(
                1L, DayOfWeek.MONDAY, LocalTime.of(9, 0))).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> workingScheduleService.create(1L, request));
        verify(workingScheduleRepository, never()).save(any(WorkingSchedule.class));
    }

    @Test
    void updateScheduleChangesTimes() {
        Doctor doctor = doctor(1L);
        WorkingSchedule schedule = new WorkingSchedule(
                doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        ReflectionTestUtils.setField(schedule, "id", 5L);
        WorkingScheduleRequest request = new WorkingScheduleRequest(
                DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), 45, false);

        when(workingScheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));
        when(workingScheduleRepository
                .existsByDoctorIdAndDayOfWeekAndStartTimeAndIdNot(
                        1L, DayOfWeek.TUESDAY, LocalTime.of(10, 0), 5L))
                .thenReturn(false);

        WorkingScheduleResponse response = workingScheduleService.update(1L, 5L, request);

        assertEquals(DayOfWeek.TUESDAY, response.dayOfWeek());
        assertEquals(LocalTime.of(10, 0), response.startTime());
        assertEquals(LocalTime.of(18, 0), response.endTime());
        assertEquals(45, response.slotDurationMinutes());
        assertEquals(false, response.active());
    }

    @Test
    void updateScheduleRejectsScheduleOfAnotherDoctor() {
        Doctor doctor = doctor(2L);
        WorkingSchedule schedule = new WorkingSchedule(
                doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        ReflectionTestUtils.setField(schedule, "id", 5L);
        WorkingScheduleRequest request = new WorkingScheduleRequest(
                DayOfWeek.TUESDAY, LocalTime.of(10, 0), LocalTime.of(18, 0), 30, true);

        when(workingScheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));

        assertThrows(ResourceNotFoundException.class,
                () -> workingScheduleService.update(1L, 5L, request));
    }

    @Test
    void deleteScheduleRemovesIt() {
        Doctor doctor = doctor(1L);
        WorkingSchedule schedule = new WorkingSchedule(
                doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        ReflectionTestUtils.setField(schedule, "id", 5L);

        when(workingScheduleRepository.findById(5L)).thenReturn(Optional.of(schedule));

        workingScheduleService.delete(1L, 5L);

        verify(workingScheduleRepository).delete(schedule);
    }

    private Doctor doctor(Long id) {
        User user = new User("doctor.user", "hash", "Doctor User");
        Specialization specialization = new Specialization("Кардиология", null);
        Doctor doctor = new Doctor(user, specialization, "Иван", "Иванов");
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }
}
