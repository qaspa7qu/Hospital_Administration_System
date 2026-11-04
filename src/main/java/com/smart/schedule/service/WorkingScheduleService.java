package com.smart.schedule.service;

import com.smart.schedule.dto.schedule.WorkingScheduleRequest;
import com.smart.schedule.dto.schedule.WorkingScheduleResponse;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.UserRepository;
import com.smart.schedule.repository.WorkingScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class WorkingScheduleService {

    private final WorkingScheduleRepository workingScheduleRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    public WorkingScheduleService(
            WorkingScheduleRepository workingScheduleRepository,
            DoctorRepository doctorRepository,
            UserRepository userRepository) {
        this.workingScheduleRepository = workingScheduleRepository;
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
    }

    public List<WorkingScheduleResponse> findByDoctor(Long doctorId) {
        findDoctor(doctorId);
        return workingScheduleRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId)
                .stream()
                .map(WorkingScheduleResponse::from)
                .toList();
    }

    public List<WorkingScheduleResponse> findByUsername(String username) {
        Doctor doctor = findDoctorByUsername(username);
        return workingScheduleRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId())
                .stream()
                .map(WorkingScheduleResponse::from)
                .toList();
    }

    @Transactional
    public WorkingScheduleResponse create(Long doctorId, WorkingScheduleRequest request) {
        Doctor doctor = findDoctor(doctorId);
        validate(request);

        if (workingScheduleRepository.existsByDoctorIdAndDayOfWeekAndStartTime(
                doctorId, request.dayOfWeek(), request.startTime())) {
            throw new ConflictException("A schedule with this day and start time already exists");
        }

        WorkingSchedule schedule = new WorkingSchedule(
                doctor, request.dayOfWeek(), request.startTime(), request.endTime());
        schedule.setSlotDurationMinutes(request.slotDurationMinutes());
        schedule.setActive(request.active() == null || request.active());

        return WorkingScheduleResponse.from(workingScheduleRepository.save(schedule));
    }

    @Transactional
    public WorkingScheduleResponse update(Long doctorId, Long scheduleId,
                                          WorkingScheduleRequest request) {
        WorkingSchedule schedule = findSchedule(doctorId, scheduleId);
        validate(request);

        if (workingScheduleRepository.existsByDoctorIdAndDayOfWeekAndStartTimeAndIdNot(
                doctorId, request.dayOfWeek(), request.startTime(), scheduleId)) {
            throw new ConflictException("A schedule with this day and start time already exists");
        }

        schedule.setDayOfWeek(request.dayOfWeek());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setSlotDurationMinutes(request.slotDurationMinutes());
        if (request.active() != null) {
            schedule.setActive(request.active());
        }

        return WorkingScheduleResponse.from(schedule);
    }

    @Transactional
    public void delete(Long doctorId, Long scheduleId) {
        WorkingSchedule schedule = findSchedule(doctorId, scheduleId);
        workingScheduleRepository.delete(schedule);
    }

    private void validate(WorkingScheduleRequest request) {
        if (request.dayOfWeek() == null || request.startTime() == null || request.endTime() == null) {
            throw new BadRequestException("Day of week and time range are required");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("Start time must be before end time");
        }
        if (request.slotDurationMinutes() < 5 || request.slotDurationMinutes() > 480) {
            throw new BadRequestException("Slot duration must be between 5 and 480 minutes");
        }
    }

    private Doctor findDoctor(Long doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor not found: " + doctorId));
    }

    private Doctor findDoctorByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));

        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor profile not found for user: " + username));
    }

    private WorkingSchedule findSchedule(Long doctorId, Long scheduleId) {
        WorkingSchedule schedule = workingScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Working schedule not found: " + scheduleId));

        if (!schedule.getDoctor().getId().equals(doctorId)) {
            throw new ResourceNotFoundException(
                    "Working schedule " + scheduleId + " does not belong to doctor " + doctorId);
        }
        return schedule;
    }
}
