package com.smart.schedule.controller;

import com.smart.schedule.dto.appointment.AppointmentResponse;
import com.smart.schedule.dto.doctor.DoctorResponse;
import com.smart.schedule.dto.doctor.UpdateDoctorProfileRequest;
import com.smart.schedule.dto.schedule.WorkingScheduleResponse;
import com.smart.schedule.service.AppointmentService;
import com.smart.schedule.service.DoctorService;
import com.smart.schedule.service.WorkingScheduleService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/doctor")
@PreAuthorize("hasRole('DOCTOR')")
public class DoctorSelfController {

    private final DoctorService doctorService;
    private final WorkingScheduleService workingScheduleService;
    private final AppointmentService appointmentService;

    public DoctorSelfController(DoctorService doctorService,
                                WorkingScheduleService workingScheduleService,
                                AppointmentService appointmentService) {
        this.doctorService = doctorService;
        this.workingScheduleService = workingScheduleService;
        this.appointmentService = appointmentService;
    }

    @GetMapping("/profile")
    public DoctorResponse profile(Authentication authentication) {
        return doctorService.findByUsername(authentication.getName());
    }

    @PutMapping("/profile")
    public DoctorResponse updateProfile(Authentication authentication,
                                        @Valid @RequestBody UpdateDoctorProfileRequest request) {
        return doctorService.updateOwnProfile(authentication.getName(), request);
    }

    @GetMapping("/schedule")
    public List<WorkingScheduleResponse> schedule(Authentication authentication) {
        return workingScheduleService.findByUsername(authentication.getName());
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments(
            Authentication authentication,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        DoctorResponse doctor = doctorService.findByUsername(authentication.getName());
        return appointmentService.findForDoctor(doctor.id(), from, to);
    }
}
