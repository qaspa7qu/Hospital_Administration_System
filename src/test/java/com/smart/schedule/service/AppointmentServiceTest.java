package com.smart.schedule.service;

import com.smart.schedule.dto.appointment.AppointmentRequest;
import com.smart.schedule.dto.appointment.AppointmentResponse;
import com.smart.schedule.entity.Appointment;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.entity.enums.AppointmentStatus;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.PatientRepository;
import com.smart.schedule.repository.WorkingScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final LocalDate MONDAY_DATE = LocalDate.of(2030, 1, 7);

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private WorkingScheduleRepository workingScheduleRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void createAppointmentComputesEndTimeFromSchedule() {
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        WorkingSchedule schedule = schedule(doctor, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30);

        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(10, 0));

        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.countOverlapping(
                10L, MONDAY_DATE, LocalTime.of(10, 0), LocalTime.of(10, 30),
                AppointmentStatus.CANCELLED)).thenReturn(0L);
        when(appointmentRepository.save(any(Appointment.class)))
                .thenAnswer(invocation -> {
                    Appointment saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 500L);
                    return saved;
                });

        AppointmentResponse response = appointmentService.create(request);

        assertEquals(LocalTime.of(10, 30), response.endTime());
        assertEquals(AppointmentStatus.SCHEDULED, response.status());
        assertEquals("Петров Пётр", response.patientFullName());
        assertEquals("Иванов Иван Иванович", response.doctorFullName());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsDayWithoutWorkingSchedule() {
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(10, 0));

        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        10L, DayOfWeek.MONDAY))
                .thenReturn(List.of());

        assertThrows(ConflictException.class, () -> appointmentService.create(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsTimeOutsideWorkingHours() {
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        WorkingSchedule schedule = schedule(doctor, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30);
        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(18, 0));

        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));

        assertThrows(ConflictException.class, () -> appointmentService.create(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsAppointmentThatDoesNotFitIntoShift() {
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        WorkingSchedule schedule = schedule(doctor, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 60);
        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(16, 30));

        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));

        assertThrows(ConflictException.class, () -> appointmentService.create(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsShiftThatCrossesMidnight() {
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        WorkingSchedule schedule = schedule(doctor, DayOfWeek.MONDAY,
                LocalTime.of(23, 0), LocalTime.of(23, 59), 60);
        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(23, 30));

        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));

        assertThrows(ConflictException.class, () -> appointmentService.create(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsOverlappingSlot() {
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        WorkingSchedule schedule = schedule(doctor, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30);
        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(10, 0));

        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.countOverlapping(
                10L, MONDAY_DATE, LocalTime.of(10, 0), LocalTime.of(10, 30),
                AppointmentStatus.CANCELLED)).thenReturn(1L);

        assertThrows(ConflictException.class, () -> appointmentService.create(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsPastDate() {
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        AppointmentRequest request = request(100L, 10L,
                LocalDate.now().minusDays(1), LocalTime.of(10, 0));

        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));

        assertThrows(BadRequestException.class, () -> appointmentService.create(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void createAppointmentRejectsInactiveDoctor() {
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, false);
        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(10, 0));

        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));

        assertThrows(ConflictException.class, () -> appointmentService.create(request));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void updateStatusCancelsScheduledAppointment() {
        Appointment appointment = appointment(500L, AppointmentStatus.SCHEDULED);

        when(appointmentRepository.findByIdForUpdate(500L))
                .thenReturn(Optional.of(appointment));

        AppointmentResponse response = appointmentService.updateStatus(
                500L, AppointmentStatus.CANCELLED);

        assertEquals(AppointmentStatus.CANCELLED, response.status());
    }

    @Test
    void updateStatusCompletesScheduledAppointment() {
        Appointment appointment = appointment(500L, AppointmentStatus.SCHEDULED);

        when(appointmentRepository.findByIdForUpdate(500L))
                .thenReturn(Optional.of(appointment));

        AppointmentResponse response = appointmentService.updateStatus(
                500L, AppointmentStatus.COMPLETED);

        assertEquals(AppointmentStatus.COMPLETED, response.status());
    }

    @Test
    void updateStatusRejectsTransitionFromTerminalState() {
        Appointment appointment = appointment(500L, AppointmentStatus.COMPLETED);

        when(appointmentRepository.findByIdForUpdate(500L))
                .thenReturn(Optional.of(appointment));

        assertThrows(ConflictException.class,
                () -> appointmentService.updateStatus(500L, AppointmentStatus.CANCELLED));
        assertEquals(AppointmentStatus.COMPLETED, appointment.getStatus());
    }

    @Test
    void updateAppointmentReschedulesAndExcludesItselfFromOverlapCheck() {
        Appointment appointment = appointment(500L, AppointmentStatus.SCHEDULED);
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        WorkingSchedule schedule = schedule(doctor, DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 45);
        AppointmentRequest request = request(100L, 10L,
                MONDAY_DATE.plusDays(1), LocalTime.of(11, 0));

        when(appointmentRepository.findByIdForUpdate(500L))
                .thenReturn(Optional.of(appointment));
        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        10L, DayOfWeek.TUESDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.countOverlappingExcluding(
                10L, MONDAY_DATE.plusDays(1), LocalTime.of(11, 0), LocalTime.of(11, 45),
                AppointmentStatus.CANCELLED, 500L)).thenReturn(0L);

        AppointmentResponse response = appointmentService.update(500L, request);

        assertEquals(MONDAY_DATE.plusDays(1), response.appointmentDate());
        assertEquals(LocalTime.of(11, 0), response.startTime());
        assertEquals(LocalTime.of(11, 45), response.endTime());
    }

    @Test
    void updateAppointmentRejectsOverlapWithAnotherAppointment() {
        Appointment appointment = appointment(500L, AppointmentStatus.SCHEDULED);
        Patient patient = patient(100L);
        Doctor doctor = doctor(10L, true);
        WorkingSchedule schedule = schedule(doctor, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30);
        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(12, 0));

        when(appointmentRepository.findByIdForUpdate(500L))
                .thenReturn(Optional.of(appointment));
        when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
        when(doctorRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(doctor));
        when(workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        10L, DayOfWeek.MONDAY))
                .thenReturn(List.of(schedule));
        when(appointmentRepository.countOverlappingExcluding(
                10L, MONDAY_DATE, LocalTime.of(12, 0), LocalTime.of(12, 30),
                AppointmentStatus.CANCELLED, 500L)).thenReturn(1L);

        assertThrows(ConflictException.class,
                () -> appointmentService.update(500L, request));
    }

    @Test
    void updateAppointmentRejectsCompletedAppointment() {
        Appointment appointment = appointment(500L, AppointmentStatus.COMPLETED);
        AppointmentRequest request = request(100L, 10L, MONDAY_DATE, LocalTime.of(10, 0));

        when(appointmentRepository.findByIdForUpdate(500L))
                .thenReturn(Optional.of(appointment));

        assertThrows(ConflictException.class,
                () -> appointmentService.update(500L, request));
        verify(doctorRepository, never()).findByIdForUpdate(any());
    }

    private AppointmentRequest request(Long patientId, Long doctorId,
                                       LocalDate date, LocalTime startTime) {
        return new AppointmentRequest(
                patientId, doctorId, date, startTime, "Консультация", "Первичный приём");
    }

    private Patient patient(Long id) {
        Patient patient = new Patient("Пётр", "Петров", "+7 900 000-00-00");
        ReflectionTestUtils.setField(patient, "id", id);
        return patient;
    }

    private Doctor doctor(Long id, boolean active) {
        User user = new User("doctor.user", "hash", "Doctor User");
        ReflectionTestUtils.setField(user, "id", 20L);

        Specialization specialization = new Specialization("Кардиология", null);
        ReflectionTestUtils.setField(specialization, "id", 30L);

        Doctor doctor = new Doctor(user, specialization, "Иван", "Иванов");
        ReflectionTestUtils.setField(doctor, "id", id);
        doctor.setMiddleName("Иванович");
        doctor.setActive(active);
        return doctor;
    }

    private WorkingSchedule schedule(Doctor doctor, DayOfWeek day,
                                     LocalTime start, LocalTime end, int slotMinutes) {
        WorkingSchedule schedule = new WorkingSchedule(doctor, day, start, end);
        schedule.setSlotDurationMinutes(slotMinutes);
        return schedule;
    }

    private Appointment appointment(Long id, AppointmentStatus status) {
        Appointment appointment = new Appointment(
                patient(100L), doctor(10L, true),
                MONDAY_DATE, LocalTime.of(9, 0), LocalTime.of(9, 30));
        ReflectionTestUtils.setField(appointment, "id", id);
        appointment.setStatus(status);
        return appointment;
    }
}
