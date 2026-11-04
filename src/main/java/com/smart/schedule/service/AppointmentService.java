package com.smart.schedule.service;

import com.smart.schedule.dto.appointment.AppointmentRequest;
import com.smart.schedule.dto.appointment.AppointmentResponse;
import com.smart.schedule.entity.Appointment;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.entity.enums.AppointmentStatus;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.PatientRepository;
import com.smart.schedule.repository.WorkingScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final WorkingScheduleRepository workingScheduleRepository;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            WorkingScheduleRepository workingScheduleRepository) {
        this.appointmentRepository = appointmentRepository;
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.workingScheduleRepository = workingScheduleRepository;
    }

    public List<AppointmentResponse> findAll(Long patientId, Long doctorId,
                                             LocalDate date, AppointmentStatus status) {
        List<Appointment> appointments = resolveAppointments(patientId, doctorId, date);

        return appointments.stream()
                .filter(appointment -> date == null || date.equals(appointment.getAppointmentDate()))
                .filter(appointment -> status == null || status == appointment.getStatus())
                .map(AppointmentResponse::from)
                .toList();
    }

    public AppointmentResponse findById(Long id) {
        return AppointmentResponse.from(findAppointment(id));
    }

    public List<AppointmentResponse> findForDoctor(Long doctorId, LocalDate from, LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new BadRequestException("Both 'from' and 'to' dates are required");
        }
        if (from != null && from.isAfter(to)) {
            throw new BadRequestException("'from' date must not be after 'to' date");
        }

        List<Appointment> appointments = (from == null)
                ? appointmentRepository.findByDoctorIdOrderByAppointmentDateDescStartTimeDesc(doctorId)
                : appointmentRepository
                        .findByDoctorIdAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
                                doctorId, from, to);

        return appointments.stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse create(AppointmentRequest request) {
        Patient patient = findPatient(request.patientId());
        Doctor doctor = lockDoctor(request.doctorId());

        LocalTime endTime = resolveEndTime(
                doctor, request.appointmentDate(), request.startTime());
        ensureSlotIsFree(
                doctor.getId(), request.appointmentDate(), request.startTime(), endTime, null);

        Appointment appointment = new Appointment(
                patient, doctor, request.appointmentDate(), request.startTime(), endTime);
        appointment.setReason(request.reason());
        appointment.setNotes(request.notes());

        return AppointmentResponse.from(appointmentRepository.save(appointment));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse update(Long id, AppointmentRequest request) {
        Appointment appointment = lockAppointment(id);
        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException(
                    "Only SCHEDULED appointments can be rescheduled");
        }

        Patient patient = findPatient(request.patientId());
        Doctor doctor = lockDoctor(request.doctorId());

        LocalTime endTime = resolveEndTime(
                doctor, request.appointmentDate(), request.startTime());
        ensureSlotIsFree(
                doctor.getId(), request.appointmentDate(), request.startTime(), endTime, id);

        appointment.setPatient(patient);
        appointment.setDoctor(doctor);
        appointment.setAppointmentDate(request.appointmentDate());
        appointment.setStartTime(request.startTime());
        appointment.setEndTime(endTime);
        appointment.setReason(request.reason());
        appointment.setNotes(request.notes());

        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, AppointmentStatus newStatus) {
        Appointment appointment = lockAppointment(id);

        if (newStatus == null) {
            throw new BadRequestException("Appointment status is required");
        }

        AppointmentStatus currentStatus = appointment.getStatus();
        if (currentStatus == newStatus) {
            return AppointmentResponse.from(appointment);
        }
        if (currentStatus != AppointmentStatus.SCHEDULED) {
            throw new ConflictException(
                    "Appointment status cannot be changed from " + currentStatus);
        }
        if (newStatus == AppointmentStatus.SCHEDULED) {
            throw new ConflictException(
                    "Appointment cannot be returned to SCHEDULED");
        }

        appointment.setStatus(newStatus);
        return AppointmentResponse.from(appointment);
    }

    private List<Appointment> resolveAppointments(Long patientId, Long doctorId, LocalDate date) {
        if (patientId != null) {
            return appointmentRepository
                    .findByPatientIdOrderByAppointmentDateDescStartTimeDesc(patientId);
        }
        if (doctorId != null && date != null) {
            return appointmentRepository
                    .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(doctorId, date);
        }
        if (doctorId != null) {
            return appointmentRepository
                    .findByDoctorIdOrderByAppointmentDateDescStartTimeDesc(doctorId);
        }
        return appointmentRepository.findAllByOrderByAppointmentDateDescStartTimeDesc();
    }

    private LocalTime resolveEndTime(Doctor doctor, LocalDate date, LocalTime startTime) {
        if (date == null || startTime == null) {
            throw new BadRequestException("Appointment date and start time are required");
        }
        if (date.isBefore(LocalDate.now())) {
            throw new BadRequestException("Cannot book an appointment in the past");
        }
        if (!doctor.isActive()) {
            throw new ConflictException("Doctor is inactive");
        }

        List<WorkingSchedule> schedules = workingScheduleRepository
                .findByDoctorIdAndDayOfWeekAndActiveTrueOrderByStartTimeAsc(
                        doctor.getId(), date.getDayOfWeek());

        WorkingSchedule schedule = schedules.stream()
                .filter(item -> !startTime.isBefore(item.getStartTime())
                        && startTime.isBefore(item.getEndTime()))
                .findFirst()
                .orElseThrow(() -> new ConflictException(
                        "Doctor does not work on " + date.getDayOfWeek()
                                + " at " + startTime));

        LocalTime endTime = startTime.plusMinutes(schedule.getSlotDurationMinutes());
        if (!endTime.isAfter(startTime) || endTime.isAfter(schedule.getEndTime())) {
            throw new ConflictException(
                    "The appointment does not fit into the doctor's working hours");
        }
        return endTime;
    }

    private void ensureSlotIsFree(Long doctorId, LocalDate date,
                                  LocalTime startTime, LocalTime endTime, Long excludedId) {
        long overlaps = (excludedId == null)
                ? appointmentRepository.countOverlapping(
                        doctorId, date, startTime, endTime, AppointmentStatus.CANCELLED)
                : appointmentRepository.countOverlappingExcluding(
                        doctorId, date, startTime, endTime,
                        AppointmentStatus.CANCELLED, excludedId);

        if (overlaps > 0) {
            throw new ConflictException(
                    "Doctor is already booked for the selected time interval");
        }
    }

    private Appointment findAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + id));
    }

    private Appointment lockAppointment(Long id) {
        return appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + id));
    }

    private Patient findPatient(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Patient not found: " + id));
    }

    private Doctor lockDoctor(Long id) {
        return doctorRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor not found: " + id));
    }
}
