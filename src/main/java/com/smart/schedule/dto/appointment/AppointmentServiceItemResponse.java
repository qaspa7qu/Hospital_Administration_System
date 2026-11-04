package com.smart.schedule.dto.appointment;

import com.smart.schedule.entity.AppointmentService;

import java.math.BigDecimal;

public record AppointmentServiceItemResponse(
        Long id,
        Long serviceId,
        String serviceName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {

    public static AppointmentServiceItemResponse from(AppointmentService item) {
        return new AppointmentServiceItemResponse(
                item.getId(),
                item.getService().getId(),
                item.getService().getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getLineTotal());
    }
}
