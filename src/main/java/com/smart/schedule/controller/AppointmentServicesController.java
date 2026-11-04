package com.smart.schedule.controller;

import com.smart.schedule.dto.appointment.AddAppointmentServiceRequest;
import com.smart.schedule.dto.appointment.AppointmentServicesResponse;
import com.smart.schedule.dto.appointment.UpdateAppointmentServiceRequest;
import com.smart.schedule.service.AppointmentBillingService;
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

@RestController
@RequestMapping("/api/appointments/{appointmentId}/services")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST')")
public class AppointmentServicesController {

    private final AppointmentBillingService appointmentBillingService;

    public AppointmentServicesController(AppointmentBillingService appointmentBillingService) {
        this.appointmentBillingService = appointmentBillingService;
    }

    @GetMapping
    public AppointmentServicesResponse findServices(@PathVariable Long appointmentId) {
        return appointmentBillingService.findServices(appointmentId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentServicesResponse addService(
            @PathVariable Long appointmentId,
            @Valid @RequestBody AddAppointmentServiceRequest request) {
        return appointmentBillingService.addService(appointmentId, request);
    }

    @PutMapping("/{itemId}")
    public AppointmentServicesResponse updateQuantity(
            @PathVariable Long appointmentId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateAppointmentServiceRequest request) {
        return appointmentBillingService.updateQuantity(appointmentId, itemId, request);
    }

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeService(@PathVariable Long appointmentId,
                              @PathVariable Long itemId) {
        appointmentBillingService.removeService(appointmentId, itemId);
    }
}
