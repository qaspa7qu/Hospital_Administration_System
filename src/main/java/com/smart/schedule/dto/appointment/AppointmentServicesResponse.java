package com.smart.schedule.dto.appointment;

import com.smart.schedule.entity.AppointmentService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record AppointmentServicesResponse(
        Long appointmentId,
        List<AppointmentServiceItemResponse> services,
        BigDecimal totalPrice
) {

    public static AppointmentServicesResponse from(
            Long appointmentId, List<AppointmentService> items) {
        List<AppointmentServiceItemResponse> services = items.stream()
                .map(AppointmentServiceItemResponse::from)
                .toList();

        BigDecimal totalPrice = items.stream()
                .map(AppointmentService::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        return new AppointmentServicesResponse(appointmentId, services, totalPrice);
    }
}
