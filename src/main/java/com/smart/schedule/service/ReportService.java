package com.smart.schedule.service;

import com.smart.schedule.dto.report.AppointmentStatisticsResponse;
import com.smart.schedule.dto.report.DoctorStatisticsItem;
import com.smart.schedule.dto.report.DoctorStatisticsResponse;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.enums.AppointmentStatus;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.AppointmentServiceRepository;
import com.smart.schedule.repository.DoctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentServiceRepository appointmentServiceRepository;
    private final DoctorRepository doctorRepository;

    public ReportService(AppointmentRepository appointmentRepository,
                         AppointmentServiceRepository appointmentServiceRepository,
                         DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentServiceRepository = appointmentServiceRepository;
        this.doctorRepository = doctorRepository;
    }

    public AppointmentStatisticsResponse appointmentStatistics(LocalDate from, LocalDate to) {
        validatePeriod(from, to);

        Map<AppointmentStatus, Long> byStatus = toStatusMap(
                appointmentRepository.countByStatusBetween(from, to));

        long scheduled = count(byStatus, AppointmentStatus.SCHEDULED);
        long completed = count(byStatus, AppointmentStatus.COMPLETED);
        long cancelled = count(byStatus, AppointmentStatus.CANCELLED);

        return new AppointmentStatisticsResponse(
                from,
                to,
                scheduled + completed + cancelled,
                scheduled,
                completed,
                cancelled,
                normalizeRevenue(appointmentServiceRepository.sumRevenueBetween(
                        from, to, AppointmentStatus.COMPLETED)));
    }

    public DoctorStatisticsResponse doctorStatistics(LocalDate from, LocalDate to, Long doctorId) {
        validatePeriod(from, to);

        List<Doctor> doctors;
        if (doctorId != null) {
            doctors = List.of(doctorRepository.findById(doctorId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Doctor not found: " + doctorId)));
        } else {
            doctors = doctorRepository.findAllByOrderByLastNameAscFirstNameAsc();
        }

        Map<Long, Map<AppointmentStatus, Long>> countsByDoctor = new HashMap<>();
        for (Object[] row : appointmentRepository.countByDoctorAndStatusBetween(from, to)) {
            Long id = ((Number) row[0]).longValue();
            AppointmentStatus status = (AppointmentStatus) row[1];
            long count = ((Number) row[2]).longValue();
            countsByDoctor
                    .computeIfAbsent(id, key -> new EnumMap<>(AppointmentStatus.class))
                    .merge(status, count, Long::sum);
        }

        Map<Long, BigDecimal> revenueByDoctor = new HashMap<>();
        for (Object[] row : appointmentServiceRepository.sumRevenueByDoctorBetween(
                from, to, AppointmentStatus.COMPLETED)) {
            Long id = ((Number) row[0]).longValue();
            revenueByDoctor.put(id, (BigDecimal) row[1]);
        }

        List<DoctorStatisticsItem> items = doctors.stream()
                .map(doctor -> toDoctorItem(
                        doctor,
                        countsByDoctor.getOrDefault(doctor.getId(), Map.of()),
                        revenueByDoctor.get(doctor.getId())))
                .toList();

        return new DoctorStatisticsResponse(from, to, items);
    }

    private DoctorStatisticsItem toDoctorItem(Doctor doctor,
                                              Map<AppointmentStatus, Long> byStatus,
                                              BigDecimal revenue) {
        long scheduled = count(byStatus, AppointmentStatus.SCHEDULED);
        long completed = count(byStatus, AppointmentStatus.COMPLETED);
        long cancelled = count(byStatus, AppointmentStatus.CANCELLED);

        return new DoctorStatisticsItem(
                doctor.getId(),
                doctor.getFullName(),
                doctor.getSpecialization().getName(),
                scheduled + completed + cancelled,
                scheduled,
                completed,
                cancelled,
                normalizeRevenue(revenue));
    }

    private Map<AppointmentStatus, Long> toStatusMap(List<Object[]> rows) {
        Map<AppointmentStatus, Long> result = new EnumMap<>(AppointmentStatus.class);
        for (Object[] row : rows) {
            AppointmentStatus status = (AppointmentStatus) row[0];
            result.merge(status, ((Number) row[1]).longValue(), Long::sum);
        }
        return result;
    }

    private long count(Map<AppointmentStatus, Long> byStatus, AppointmentStatus status) {
        return byStatus.getOrDefault(status, 0L);
    }

    private BigDecimal normalizeRevenue(BigDecimal revenue) {
        return (revenue == null ? BigDecimal.ZERO : revenue)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("Report period requires both 'from' and 'to' dates");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("'from' date must not be after 'to' date");
        }
    }
}
