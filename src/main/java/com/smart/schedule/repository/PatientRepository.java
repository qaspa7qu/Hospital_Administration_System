package com.smart.schedule.repository;

import com.smart.schedule.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPhone(String phone);

    List<Patient> findByLastNameContainingIgnoreCaseOrderByLastNameAsc(String lastName);

    List<Patient> findAllByOrderByLastNameAscFirstNameAsc();
}
