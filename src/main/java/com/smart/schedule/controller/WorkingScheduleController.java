package com.smart.schedule.controller;

import com.smart.schedule.dto.schedule.WorkingScheduleRequest;
import com.smart.schedule.dto.schedule.WorkingScheduleResponse;
import com.smart.schedule.service.WorkingScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/doctors/{doctorId}/schedule")
public class WorkingScheduleController {

    private final WorkingScheduleService workingScheduleService;

    public WorkingScheduleController(WorkingScheduleService workingScheduleService) {
        this.workingScheduleService = workingScheduleService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'DOCTOR')")
    public List<WorkingScheduleResponse> findByDoctor(@PathVariable Long doctorId) {
        return workingScheduleService.findByDoctor(doctorId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public WorkingScheduleResponse create(@PathVariable Long doctorId,
                                          @Valid @RequestBody WorkingScheduleRequest request) {
        return workingScheduleService.create(doctorId, request);
    }

    @PutMapping("/{scheduleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkingScheduleResponse update(@PathVariable Long doctorId,
                                          @PathVariable Long scheduleId,
                                          @Valid @RequestBody WorkingScheduleRequest request) {
        return workingScheduleService.update(doctorId, scheduleId, request);
    }

    @DeleteMapping("/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long doctorId, @PathVariable Long scheduleId) {
        workingScheduleService.delete(doctorId, scheduleId);
    }
}
