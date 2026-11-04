package com.smart.schedule.service;

import com.smart.schedule.dto.appointment.AddAppointmentServiceRequest;
import com.smart.schedule.dto.appointment.AppointmentServicesResponse;
import com.smart.schedule.dto.appointment.UpdateAppointmentServiceRequest;
import com.smart.schedule.entity.Appointment;
import com.smart.schedule.entity.AppointmentService;
import com.smart.schedule.entity.Service;
import com.smart.schedule.entity.enums.AppointmentStatus;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.AppointmentServiceRepository;
import com.smart.schedule.repository.ServiceRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class AppointmentBillingService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentServiceRepository appointmentServiceRepository;
    private final ServiceRepository serviceRepository;

    public AppointmentBillingService(
            AppointmentRepository appointmentRepository,
            AppointmentServiceRepository appointmentServiceRepository,
            ServiceRepository serviceRepository) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentServiceRepository = appointmentServiceRepository;
        this.serviceRepository = serviceRepository;
    }

    public AppointmentServicesResponse findServices(Long appointmentId) {
        findAppointment(appointmentId);
        return buildResponse(appointmentId);
    }

    @Transactional
    public AppointmentServicesResponse addService(
            Long appointmentId, AddAppointmentServiceRequest request) {
        Appointment appointment = lockScheduledAppointment(appointmentId);
        Service service = findService(request.serviceId());

        if (!service.isActive()) {
            throw new ConflictException("Service is inactive: " + service.getName());
        }
        if (appointmentServiceRepository
                .findByAppointmentIdAndServiceId(appointmentId, service.getId())
                .isPresent()) {
            throw new ConflictException(
                    "Service is already added to this appointment: " + service.getName());
        }

        int quantity = request.quantity() == null ? 1 : request.quantity();
        validateQuantity(quantity);

        BigDecimal unitPrice = service.getPrice() == null
                ? BigDecimal.ZERO
                : service.getPrice();

        AppointmentService item = new AppointmentService(
                appointment, service, quantity, unitPrice);
        appointment.addServiceItem(item);
        appointmentServiceRepository.save(item);

        return buildResponse(appointmentId);
    }

    @Transactional
    public AppointmentServicesResponse updateQuantity(
            Long appointmentId, Long itemId,
            UpdateAppointmentServiceRequest request) {
        lockScheduledAppointment(appointmentId);
        AppointmentService item = findItem(appointmentId, itemId);

        validateQuantity(request.quantity());
        item.setQuantity(request.quantity());

        return buildResponse(appointmentId);
    }

    @Transactional
    public void removeService(Long appointmentId, Long itemId) {
        Appointment appointment = lockScheduledAppointment(appointmentId);
        AppointmentService item = findItem(appointmentId, itemId);

        appointment.getServiceItems()
                .removeIf(existing -> existing.getId().equals(item.getId()));
        appointmentServiceRepository.delete(item);
    }

    private AppointmentServicesResponse buildResponse(Long appointmentId) {
        List<AppointmentService> items = appointmentServiceRepository
                .findByAppointmentIdOrderByIdAsc(appointmentId);
        return AppointmentServicesResponse.from(appointmentId, items);
    }

    private Appointment findAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + id));
    }

    private Appointment lockScheduledAppointment(Long id) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + id));

        if (appointment.getStatus() != AppointmentStatus.SCHEDULED) {
            throw new ConflictException(
                    "Services can only be changed for SCHEDULED appointments");
        }
        return appointment;
    }

    private Service findService(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found: " + id));
    }

    private AppointmentService findItem(Long appointmentId, Long itemId) {
        AppointmentService item = appointmentServiceRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment service item not found: " + itemId));

        if (!item.getAppointment().getId().equals(appointmentId)) {
            throw new ResourceNotFoundException(
                    "Appointment service item " + itemId
                            + " does not belong to appointment " + appointmentId);
        }
        return item;
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 100) {
            throw new BadRequestException(
                    "Service quantity must be between 1 and 100");
        }
    }
}
