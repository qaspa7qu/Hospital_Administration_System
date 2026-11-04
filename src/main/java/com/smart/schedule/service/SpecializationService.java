package com.smart.schedule.service;

import com.smart.schedule.dto.specialization.SpecializationRequest;
import com.smart.schedule.dto.specialization.SpecializationResponse;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.SpecializationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SpecializationService {

    private final SpecializationRepository specializationRepository;
    private final DoctorRepository doctorRepository;

    public SpecializationService(SpecializationRepository specializationRepository,
                                 DoctorRepository doctorRepository) {
        this.specializationRepository = specializationRepository;
        this.doctorRepository = doctorRepository;
    }

    public List<SpecializationResponse> findAll() {
        return specializationRepository.findAllByOrderByNameAsc().stream()
                .map(SpecializationResponse::from)
                .toList();
    }

    public SpecializationResponse findById(Long id) {
        return SpecializationResponse.from(findEntity(id));
    }

    @Transactional
    public SpecializationResponse create(SpecializationRequest request) {
        if (specializationRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("Specialization already exists: " + request.name());
        }

        Specialization specialization = new Specialization(request.name(), request.description());
        return SpecializationResponse.from(specializationRepository.save(specialization));
    }

    @Transactional
    public SpecializationResponse update(Long id, SpecializationRequest request) {
        Specialization specialization = findEntity(id);

        if (specializationRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new ConflictException("Specialization already exists: " + request.name());
        }

        specialization.setName(request.name());
        specialization.setDescription(request.description());
        return SpecializationResponse.from(specialization);
    }

    @Transactional
    public void delete(Long id) {
        Specialization specialization = findEntity(id);
        if (doctorRepository.existsBySpecializationId(id)) {
            throw new ConflictException(
                    "Cannot delete a specialization used by doctors");
        }
        specializationRepository.delete(specialization);
    }

    private Specialization findEntity(Long id) {
        return specializationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Specialization not found: " + id));
    }
}
