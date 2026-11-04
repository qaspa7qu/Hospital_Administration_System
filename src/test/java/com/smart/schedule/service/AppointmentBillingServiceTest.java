package com.smart.schedule.service;

import com.smart.schedule.dto.appointment.AddAppointmentServiceRequest;
import com.smart.schedule.dto.appointment.AppointmentServicesResponse;
import com.smart.schedule.dto.appointment.UpdateAppointmentServiceRequest;
import com.smart.schedule.entity.Appointment;
import com.smart.schedule.entity.AppointmentService;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.entity.Service;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.enums.AppointmentStatus;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.AppointmentServiceRepository;
import com.smart.schedule.repository.ServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentBillingServiceTest {

    private static final Long APPOINTMENT_ID = 500L;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentServiceRepository appointmentServiceRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private AppointmentBillingService billingService;

    private Appointment appointment;
    private List<AppointmentService> storedItems;

    @BeforeEach
    void setUp() {
        appointment = appointment(AppointmentStatus.SCHEDULED);
        storedItems = new ArrayList<>();
    }

    @Test
    void addServiceSnapshotsCatalogPriceAndComputesTotal() {
        Service service = service(1L, "Консультация", "1500.00");

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(appointmentServiceRepository.findByAppointmentIdAndServiceId(
                APPOINTMENT_ID, 1L)).thenReturn(Optional.empty());
        when(appointmentServiceRepository.save(any(AppointmentService.class)))
                .thenAnswer(invocation -> {
                    AppointmentService saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 700L);
                    storedItems.add(saved);
                    return saved;
                });
        when(appointmentServiceRepository.findByAppointmentIdOrderByIdAsc(APPOINTMENT_ID))
                .thenReturn(storedItems);

        AppointmentServicesResponse response = billingService.addService(
                APPOINTMENT_ID, new AddAppointmentServiceRequest(1L, 2));

        assertEquals(1, response.services().size());
        assertEquals(2, response.services().get(0).quantity());
        assertEquals(new BigDecimal("1500.00"),
                response.services().get(0).unitPrice());
        assertEquals(new BigDecimal("3000.00"),
                response.services().get(0).lineTotal());
        assertEquals(new BigDecimal("3000.00"), response.totalPrice());
        assertEquals(new BigDecimal("3000.00"), appointment.getTotalPrice());
    }

    @Test
    void addFreeServiceProducesZeroTotal() {
        Service service = service(1L, "Осмотр", "0.00");

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(appointmentServiceRepository.findByAppointmentIdAndServiceId(
                APPOINTMENT_ID, 1L)).thenReturn(Optional.empty());
        when(appointmentServiceRepository.save(any(AppointmentService.class)))
                .thenAnswer(invocation -> {
                    AppointmentService saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 700L);
                    storedItems.add(saved);
                    return saved;
                });
        when(appointmentServiceRepository.findByAppointmentIdOrderByIdAsc(APPOINTMENT_ID))
                .thenReturn(storedItems);

        AppointmentServicesResponse response = billingService.addService(
                APPOINTMENT_ID, new AddAppointmentServiceRequest(1L, null));

        assertEquals(1, response.services().get(0).quantity());
        assertEquals(0, response.totalPrice().compareTo(BigDecimal.ZERO));
    }

    @Test
    void addServiceRejectsDuplicateAttachment() {
        Service service = service(1L, "Консультация", "1500.00");

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));
        when(appointmentServiceRepository.findByAppointmentIdAndServiceId(
                APPOINTMENT_ID, 1L))
                .thenReturn(Optional.of(new AppointmentService(
                        appointment, service, 1, service.getPrice())));

        assertThrows(ConflictException.class, () -> billingService.addService(
                APPOINTMENT_ID, new AddAppointmentServiceRequest(1L, 1)));
        verify(appointmentServiceRepository, never()).save(any(AppointmentService.class));
    }

    @Test
    void addServiceRejectsInactiveService() {
        Service service = service(1L, "Архивная услуга", "100.00");
        service.setActive(false);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(service));

        assertThrows(ConflictException.class, () -> billingService.addService(
                APPOINTMENT_ID, new AddAppointmentServiceRequest(1L, 1)));
        verify(appointmentServiceRepository, never()).save(any(AppointmentService.class));
    }

    @Test
    void cannotChangeServicesOfCompletedAppointment() {
        Appointment completed = appointment(AppointmentStatus.COMPLETED);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(completed));

        assertThrows(ConflictException.class, () -> billingService.addService(
                APPOINTMENT_ID, new AddAppointmentServiceRequest(1L, 1)));
        verify(serviceRepository, never()).findById(any());
    }

    @Test
    void cannotRemoveServicesFromCancelledAppointment() {
        Appointment cancelled = appointment(AppointmentStatus.CANCELLED);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(cancelled));

        assertThrows(ConflictException.class,
                () -> billingService.removeService(APPOINTMENT_ID, 700L));
    }

    @Test
    void updateQuantityRecalculatesTotal() {
        Service service = service(1L, "Консультация", "1500.00");
        AppointmentService item = item(700L, appointment, service, 2);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(appointmentServiceRepository.findById(700L))
                .thenReturn(Optional.of(item));
        when(appointmentServiceRepository.findByAppointmentIdOrderByIdAsc(APPOINTMENT_ID))
                .thenReturn(List.of(item));

        AppointmentServicesResponse response = billingService.updateQuantity(
                APPOINTMENT_ID, 700L, new UpdateAppointmentServiceRequest(3));

        assertEquals(3, response.services().get(0).quantity());
        assertEquals(new BigDecimal("4500.00"), response.totalPrice());
    }

    @Test
    void updateQuantityRejectsOutOfRangeValue() {
        Service service = service(1L, "Консультация", "1500.00");
        AppointmentService item = item(700L, appointment, service, 1);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(appointmentServiceRepository.findById(700L))
                .thenReturn(Optional.of(item));

        assertThrows(BadRequestException.class, () -> billingService.updateQuantity(
                APPOINTMENT_ID, 700L, new UpdateAppointmentServiceRequest(0)));
        assertEquals(1, item.getQuantity());
    }

    @Test
    void removeServiceDeletesItemAndUpdatesTotal() {
        Service service = service(1L, "Консультация", "1500.00");
        AppointmentService item = item(700L, appointment, service, 2);
        appointment.addServiceItem(item);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(appointmentServiceRepository.findById(700L))
                .thenReturn(Optional.of(item));

        billingService.removeService(APPOINTMENT_ID, 700L);

        assertTrue(appointment.getServiceItems().isEmpty());
        assertEquals(0, appointment.getTotalPrice().compareTo(BigDecimal.ZERO));
        verify(appointmentServiceRepository).delete(item);
    }

    @Test
    void rejectsItemThatBelongsToAnotherAppointment() {
        Appointment other = appointment(AppointmentStatus.SCHEDULED);
        ReflectionTestUtils.setField(other, "id", 501L);
        Service service = service(1L, "Консультация", "1500.00");
        AppointmentService item = item(700L, other, service, 1);

        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(appointmentServiceRepository.findById(700L))
                .thenReturn(Optional.of(item));

        assertThrows(ResourceNotFoundException.class,
                () -> billingService.removeService(APPOINTMENT_ID, 700L));
    }

    @Test
    void findServicesReturnsEmptyListWhenNothingAttached() {
        when(appointmentRepository.findById(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(appointmentServiceRepository.findByAppointmentIdOrderByIdAsc(APPOINTMENT_ID))
                .thenReturn(List.of());

        AppointmentServicesResponse response = billingService.findServices(APPOINTMENT_ID);

        assertEquals(0, response.services().size());
        assertEquals(0, response.totalPrice().compareTo(BigDecimal.ZERO));
    }

    @Test
    void addServiceThrowsWhenAppointmentDoesNotExist() {
        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> billingService.addService(
                APPOINTMENT_ID, new AddAppointmentServiceRequest(1L, 1)));
    }

    @Test
    void addServiceThrowsWhenServiceDoesNotExist() {
        when(appointmentRepository.findByIdForUpdate(APPOINTMENT_ID))
                .thenReturn(Optional.of(appointment));
        when(serviceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> billingService.addService(
                APPOINTMENT_ID, new AddAppointmentServiceRequest(1L, 1)));
    }

    private Appointment appointment(AppointmentStatus status) {
        Appointment appointment = new Appointment(
                patient(), doctor(), LocalDate.of(2030, 1, 7),
                LocalTime.of(10, 0), LocalTime.of(10, 30));
        ReflectionTestUtils.setField(appointment, "id", APPOINTMENT_ID);
        appointment.setStatus(status);
        return appointment;
    }

    private AppointmentService item(Long id, Appointment appointment,
                                    Service service, int quantity) {
        AppointmentService item = new AppointmentService(
                appointment, service, quantity, service.getPrice());
        ReflectionTestUtils.setField(item, "id", id);
        return item;
    }

    private Service service(Long id, String name, String price) {
        Service service = new Service(name, new BigDecimal(price), 30);
        ReflectionTestUtils.setField(service, "id", id);
        return service;
    }

    private Patient patient() {
        Patient patient = new Patient("Пётр", "Петров", "+7 900 000-00-00");
        ReflectionTestUtils.setField(patient, "id", 100L);
        return patient;
    }

    private Doctor doctor() {
        User user = new User("doctor", "hash", "Doctor");
        ReflectionTestUtils.setField(user, "id", 200L);
        Specialization specialization = new Specialization("Кардиология", null);
        ReflectionTestUtils.setField(specialization, "id", 300L);
        Doctor doctor = new Doctor(user, specialization, "Иван", "Иванов");
        ReflectionTestUtils.setField(doctor, "id", 400L);
        return doctor;
    }
}
