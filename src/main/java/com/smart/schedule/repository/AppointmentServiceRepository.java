package com.smart.schedule.repository;

import com.smart.schedule.entity.AppointmentService;
import com.smart.schedule.entity.enums.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AppointmentServiceRepository extends JpaRepository<AppointmentService, Long> {

    List<AppointmentService> findByAppointmentIdOrderByIdAsc(Long appointmentId);

    Optional<AppointmentService> findByAppointmentIdAndServiceId(Long appointmentId, Long serviceId);

    boolean existsByServiceId(Long serviceId);

    @Query("""
            select sum(item.unitPrice * item.quantity)
            from AppointmentService item
            where item.appointment.appointmentDate between :from and :to
              and item.appointment.status = :status
            """)
    BigDecimal sumRevenueBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") AppointmentStatus status);

    @Query("""
            select item.appointment.doctor.id, sum(item.unitPrice * item.quantity)
            from AppointmentService item
            where item.appointment.appointmentDate between :from and :to
              and item.appointment.status = :status
            group by item.appointment.doctor.id
            """)
    List<Object[]> sumRevenueByDoctorBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") AppointmentStatus status);
}
