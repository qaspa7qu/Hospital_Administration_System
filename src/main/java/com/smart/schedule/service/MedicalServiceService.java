package com.smart.schedule.service;

import com.smart.schedule.dto.service.ServiceRequest;
import com.smart.schedule.dto.service.ServiceResponse;
import com.smart.schedule.entity.Service;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentServiceRepository;
import com.smart.schedule.repository.ServiceRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class MedicalServiceService {

    private final ServiceRepository serviceRepository;
    private final AppointmentServiceRepository appointmentServiceRepository;

    public MedicalServiceService(ServiceRepository serviceRepository,
                                 AppointmentServiceRepository appointmentServiceRepository) {
        this.serviceRepository = serviceRepository;
        this.appointmentServiceRepository = appointmentServiceRepository;
    }

    public List<ServiceResponse> findAll(boolean activeOnly) {
        List<Service> services = activeOnly
                ? serviceRepository.findAllByActiveTrueOrderByNameAsc()
                : serviceRepository.findAllByOrderByNameAsc();

        return services.stream()
                .map(ServiceResponse::from)
                .toList();
    }

    public ServiceResponse findById(Long id) {
        return ServiceResponse.from(findEntity(id));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        if (serviceRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Service already exists: " + request.name());
        }

        Service service = new Service(
                request.name(),
                request.price() == null ? BigDecimal.ZERO : request.price(),
                request.durationMinutes());
        applyRequest(service, request);
        service.setActive(request.active() == null || request.active());

        return ServiceResponse.from(serviceRepository.save(service));
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {
        Service service = findEntity(id);

        if (serviceRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("Service already exists: " + request.name());
        }

        service.setName(request.name());
        service.setPrice(request.price() == null ? BigDecimal.ZERO : request.price());
        service.setDurationMinutes(request.durationMinutes());
        applyRequest(service, request);
        if (request.active() != null) {
            service.setActive(request.active());
        }

        return ServiceResponse.from(service);
    }

    @Transactional
    public void delete(Long id) {
        Service service = findEntity(id);
        if (appointmentServiceRepository.existsByServiceId(id)) {
            throw new ConflictException(
                    "Cannot delete a service used in appointments");
        }
        serviceRepository.delete(service);
    }

    private void applyRequest(Service service, ServiceRequest request) {
        service.setDescription(request.description());
    }

    private Service findEntity(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found: " + id));
    }
}
