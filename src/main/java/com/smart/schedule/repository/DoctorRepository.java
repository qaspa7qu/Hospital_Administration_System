package com.smart.schedule.repository;

import com.smart.schedule.entity.Doctor;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    Optional<Doctor> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

    List<Doctor> findBySpecializationIdAndActiveTrue(Long specializationId);

    List<Doctor> findByActiveTrueOrderByLastNameAsc();

    List<Doctor> findAllByOrderByLastNameAscFirstNameAsc();

    List<Doctor> findBySpecializationIdOrderByLastNameAscFirstNameAsc(Long specializationId);

    boolean existsByUserId(Long userId);

    boolean existsBySpecializationId(Long specializationId);
}
