package com.smart.schedule.repository;

import com.smart.schedule.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<Service, Long> {

    List<Service> findAllByOrderByNameAsc();

    List<Service> findAllByActiveTrueOrderByNameAsc();

    Optional<Service> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
