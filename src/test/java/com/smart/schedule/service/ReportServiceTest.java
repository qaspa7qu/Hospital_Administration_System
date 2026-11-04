package com.smart.schedule.service;

import com.smart.schedule.dto.report.AppointmentStatisticsResponse;
import com.smart.schedule.dto.report.DoctorStatisticsItem;
import com.smart.schedule.dto.report.DoctorStatisticsResponse;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.enums.AppointmentStatus;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.AppointmentServiceRepository;
import com.smart.schedule.repository.DoctorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    private static final LocalDate FROM = LocalDate.of(2030, 1, 1);
    private static final LocalDate TO = LocalDate.of(2030, 1, 31);

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentServiceRepository appointmentServiceRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private ReportService reportService;

    @Test
    void appointmentStatisticsAggregatesAllStatusesAndCompletedRevenue() {
        when(appointmentRepository.countByStatusBetween(FROM, TO))
                .thenReturn(List.<Object[]>of(
                        new Object[]{AppointmentStatus.SCHEDULED, 2L},
                        new Object[]{AppointmentStatus.COMPLETED, 3L},
                        new Object[]{AppointmentStatus.CANCELLED, 1L}));
        when(appointmentServiceRepository.sumRevenueBetween(
                FROM, TO, AppointmentStatus.COMPLETED))
                .thenReturn(new BigDecimal("4500.5"));

        AppointmentStatisticsResponse response =
                reportService.appointmentStatistics(FROM, TO);

        assertEquals(FROM, response.from());
        assertEquals(TO, response.to());
        assertEquals(6, response.totalAppointments());
        assertEquals(2, response.scheduledAppointments());
        assertEquals(3, response.completedAppointments());
        assertEquals(1, response.cancelledAppointments());
        assertEquals(new BigDecimal("4500.50"), response.completedRevenue());
    }

    @Test
    void appointmentStatisticsReturnsZeroesForEmptyPeriod() {
        when(appointmentRepository.countByStatusBetween(FROM, TO)).thenReturn(List.of());
        when(appointmentServiceRepository.sumRevenueBetween(
                FROM, TO, AppointmentStatus.COMPLETED)).thenReturn(null);

        AppointmentStatisticsResponse response =
                reportService.appointmentStatistics(FROM, TO);

        assertEquals(0, response.totalAppointments());
        assertEquals(0, response.scheduledAppointments());
        assertEquals(0, response.completedAppointments());
        assertEquals(0, response.cancelledAppointments());
        assertEquals(new BigDecimal("0.00"), response.completedRevenue());
    }

    @Test
    void reportsRejectPeriodWhereFromIsAfterTo() {
        assertThrows(BadRequestException.class,
                () -> reportService.appointmentStatistics(TO, FROM));
        assertThrows(BadRequestException.class,
                () -> reportService.doctorStatistics(TO, FROM, null));

        verifyNoInteractions(appointmentRepository,
                appointmentServiceRepository, doctorRepository);
    }

    @Test
    void reportsRejectMissingPeriodDates() {
        assertThrows(BadRequestException.class,
                () -> reportService.appointmentStatistics(null, TO));
        assertThrows(BadRequestException.class,
                () -> reportService.appointmentStatistics(FROM, null));
    }

    @Test
    void doctorStatisticsIncludesDoctorsWithNoActivity() {
        Doctor first = doctor(10L, "Иванов", "Иван", "Кардиология");
        Doctor second = doctor(20L, "Петрова", "Мария", "Неврология");
        Doctor third = doctor(30L, "Сидоров", "Пётр", "Хирургия");

        when(doctorRepository.findAllByOrderByLastNameAscFirstNameAsc())
                .thenReturn(List.of(first, second, third));
        when(appointmentRepository.countByDoctorAndStatusBetween(FROM, TO))
                .thenReturn(List.<Object[]>of(
                        new Object[]{10L, AppointmentStatus.COMPLETED, 2L},
                        new Object[]{10L, AppointmentStatus.SCHEDULED, 1L},
                        new Object[]{20L, AppointmentStatus.CANCELLED, 1L}));
        when(appointmentServiceRepository.sumRevenueByDoctorBetween(
                FROM, TO, AppointmentStatus.COMPLETED))
                .thenReturn(List.<Object[]>of(new Object[]{10L, new BigDecimal("3000.00")}));

        DoctorStatisticsResponse response =
                reportService.doctorStatistics(FROM, TO, null);

        assertEquals(3, response.doctors().size());

        DoctorStatisticsItem firstItem = response.doctors().get(0);
        assertEquals(10L, firstItem.doctorId());
        assertEquals("Иванов Иван", firstItem.doctorFullName());
        assertEquals("Кардиология", firstItem.specializationName());
        assertEquals(3, firstItem.totalAppointments());
        assertEquals(1, firstItem.scheduledAppointments());
        assertEquals(2, firstItem.completedAppointments());
        assertEquals(0, firstItem.cancelledAppointments());
        assertEquals(new BigDecimal("3000.00"), firstItem.completedRevenue());

        DoctorStatisticsItem secondItem = response.doctors().get(1);
        assertEquals(1, secondItem.totalAppointments());
        assertEquals(1, secondItem.cancelledAppointments());
        assertEquals(new BigDecimal("0.00"), secondItem.completedRevenue());

        DoctorStatisticsItem thirdItem = response.doctors().get(2);
        assertEquals(0, thirdItem.totalAppointments());
        assertEquals(0, thirdItem.completedAppointments());
        assertEquals(new BigDecimal("0.00"), thirdItem.completedRevenue());
    }

    @Test
    void doctorStatisticsCanFilterByDoctorId() {
        Doctor doctor = doctor(10L, "Иванов", "Иван", "Кардиология");

        when(doctorRepository.findById(10L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.countByDoctorAndStatusBetween(FROM, TO))
                .thenReturn(List.<Object[]>of(
                        new Object[]{10L, AppointmentStatus.COMPLETED, 1L}));
        when(appointmentServiceRepository.sumRevenueByDoctorBetween(
                FROM, TO, AppointmentStatus.COMPLETED))
                .thenReturn(List.<Object[]>of(new Object[]{10L, new BigDecimal("1500")}));

        DoctorStatisticsResponse response =
                reportService.doctorStatistics(FROM, TO, 10L);

        assertEquals(1, response.doctors().size());
        assertEquals(1, response.doctors().get(0).completedAppointments());
        assertEquals(new BigDecimal("1500.00"),
                response.doctors().get(0).completedRevenue());
        verify(doctorRepository, never()).findAllByOrderByLastNameAscFirstNameAsc();
    }

    @Test
    void doctorStatisticsRejectsUnknownDoctor() {
        when(doctorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> reportService.doctorStatistics(FROM, TO, 99L));
        verify(appointmentRepository, never())
                .countByDoctorAndStatusBetween(any(), any());
    }

    private Doctor doctor(Long id, String lastName, String firstName,
                          String specializationName) {
        User user = new User("doctor." + id, "hash", firstName + " " + lastName);
        ReflectionTestUtils.setField(user, "id", id + 1000);
        Specialization specialization = new Specialization(specializationName, null);
        ReflectionTestUtils.setField(specialization, "id", id + 2000);
        Doctor doctor = new Doctor(user, specialization, firstName, lastName);
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }
}
