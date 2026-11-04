package com.smart.schedule.repository;

import com.smart.schedule.entity.Appointment;
import com.smart.schedule.entity.enums.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByPatientIdOrderByAppointmentDateDescStartTimeDesc(Long patientId);

    List<Appointment> findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(Long doctorId, LocalDate date);

    List<Appointment> findByDoctorIdOrderByAppointmentDateDescStartTimeDesc(Long doctorId);

    List<Appointment> findAllByOrderByAppointmentDateDescStartTimeDesc();

    List<Appointment> findByDoctorIdAndAppointmentDateBetweenOrderByAppointmentDateAscStartTimeAsc(
            Long doctorId, LocalDate from, LocalDate to);

    boolean existsByDoctorIdAndAppointmentDateAndStartTimeAndStatusNot(
            Long doctorId, LocalDate date, LocalTime startTime, AppointmentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select a.status, count(a)
            from Appointment a
            where a.appointmentDate between :from and :to
            group by a.status
            """)
    List<Object[]> countByStatusBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select a.doctor.id, a.status, count(a)
            from Appointment a
            where a.appointmentDate between :from and :to
            group by a.doctor.id, a.status
            """)
    List<Object[]> countByDoctorAndStatusBetween(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("""
            select count(a)
            from Appointment a
            where a.doctor.id = :doctorId
              and a.appointmentDate = :date
              and a.status <> :excludedStatus
              and a.startTime < :endTime
              and a.endTime > :startTime
            """)
    long countOverlapping(
            @Param("doctorId") Long doctorId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludedStatus") AppointmentStatus excludedStatus);

    @Query("""
            select count(a)
            from Appointment a
            where a.doctor.id = :doctorId
              and a.appointmentDate = :date
              and a.status <> :excludedStatus
              and a.startTime < :endTime
              and a.endTime > :startTime
              and a.id <> :excludedId
            """)
    long countOverlappingExcluding(
            @Param("doctorId") Long doctorId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludedStatus") AppointmentStatus excludedStatus,
            @Param("excludedId") Long excludedId);

    boolean existsByPatientId(Long patientId);

    boolean existsByDoctorId(Long doctorId);
}
