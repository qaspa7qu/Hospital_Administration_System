package com.smart.schedule.service;

import com.smart.schedule.dto.service.ServiceRequest;
import com.smart.schedule.dto.service.ServiceResponse;
import com.smart.schedule.entity.Service;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentServiceRepository;
import com.smart.schedule.repository.ServiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalServiceServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private AppointmentServiceRepository appointmentServiceRepository;

    @InjectMocks
    private MedicalServiceService medicalServiceService;

    @Test
    void createStoresPriceAndDuration() {
        ServiceRequest request = new ServiceRequest(
                "Консультация", "Первичный приём",
                new BigDecimal("1500.00"), 30, true);

        when(serviceRepository.existsByNameIgnoreCase("Консультация")).thenReturn(false);
        when(serviceRepository.save(any(Service.class))).thenAnswer(invocation -> {
            Service saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        ServiceResponse response = medicalServiceService.create(request);

        assertEquals(1L, response.id());
        assertEquals("Консультация", response.name());
        assertEquals(new BigDecimal("1500.00"), response.price());
        assertEquals(30, response.durationMinutes());
        assertTrue(response.active());
        verify(serviceRepository).save(any(Service.class));
    }

    @Test
    void createAllowsFreeServiceWithZeroPrice() {
        ServiceRequest request = new ServiceRequest(
                "Осмотр", null, BigDecimal.ZERO, 15, true);

        when(serviceRepository.existsByNameIgnoreCase("Осмотр")).thenReturn(false);
        when(serviceRepository.save(any(Service.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServiceResponse response = medicalServiceService.create(request);

        assertEquals(0, response.price().compareTo(BigDecimal.ZERO));
    }

    @Test
    void createRejectsDuplicateNameIgnoringCase() {
        ServiceRequest request = new ServiceRequest(
                "Консультация", null, new BigDecimal("1500.00"), 30, true);

        when(serviceRepository.existsByNameIgnoreCase("Консультация")).thenReturn(true);

        assertThrows(ConflictException.class, () -> medicalServiceService.create(request));
        verify(serviceRepository, never()).save(any(Service.class));
    }

    @Test
    void createWithNullActiveDefaultsToActive() {
        ServiceRequest request = new ServiceRequest(
                "Процедура", null, new BigDecimal("300.00"), 20, null);

        when(serviceRepository.existsByNameIgnoreCase("Процедура")).thenReturn(false);
        when(serviceRepository.save(any(Service.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServiceResponse response = medicalServiceService.create(request);

        assertTrue(response.active());
    }

    @Test
    void updateChangesFieldsAndRejectsDuplicateName() {
        Service service = service(1L, "Консультация", "1000.00");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(serviceRepository.existsByNameIgnoreCaseAndIdNot("Приём", 1L))
                .thenReturn(false);

        ServiceResponse response = medicalServiceService.update(1L,
                new ServiceRequest("Приём", "Повторный приём",
                        new BigDecimal("1200.00"), 45, false));

        assertEquals("Приём", response.name());
        assertEquals(new BigDecimal("1200.00"), response.price());
        assertEquals(45, response.durationMinutes());
        assertFalse(response.active());

        when(serviceRepository.existsByNameIgnoreCaseAndIdNot("Другая услуга", 1L))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> medicalServiceService.update(1L,
                new ServiceRequest("Другая услуга", null,
                        new BigDecimal("100.00"), 15, true)));
    }

    @Test
    void updateKeepsCurrentActiveFlagWhenRequestOmitsIt() {
        Service service = service(1L, "Консультация", "1000.00");
        service.setActive(false);

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(serviceRepository.existsByNameIgnoreCaseAndIdNot("Консультация", 1L))
                .thenReturn(false);

        ServiceResponse response = medicalServiceService.update(1L,
                new ServiceRequest("Консультация", null,
                        new BigDecimal("1000.00"), 30, null));

        assertFalse(response.active());
    }

    @Test
    void deleteRejectsServiceUsedInAppointments() {
        Service service = service(1L, "Консультация", "1000.00");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(appointmentServiceRepository.existsByServiceId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> medicalServiceService.delete(1L));
        verify(serviceRepository, never()).delete(any(Service.class));
    }

    @Test
    void deleteRemovesUnusedService() {
        Service service = service(1L, "Консультация", "1000.00");

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(appointmentServiceRepository.existsByServiceId(1L)).thenReturn(false);

        medicalServiceService.delete(1L);

        verify(serviceRepository).delete(service);
    }

    @Test
    void findByIdThrowsWhenServiceDoesNotExist() {
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> medicalServiceService.findById(99L));
    }

    @Test
    void findAllCanReturnOnlyActiveServices() {
        Service active = service(1L, "Консультация", "1000.00");
        Service inactive = service(2L, "Старая услуга", "500.00");
        inactive.setActive(false);

        when(serviceRepository.findAllByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(active));
        when(serviceRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(active, inactive));

        List<ServiceResponse> activeOnly = medicalServiceService.findAll(true);
        List<ServiceResponse> all = medicalServiceService.findAll(false);

        assertEquals(1, activeOnly.size());
        assertEquals(2, all.size());
    }

    private Service service(Long id, String name, String price) {
        Service service = new Service(name, new BigDecimal(price), 30);
        ReflectionTestUtils.setField(service, "id", id);
        return service;
    }
}
