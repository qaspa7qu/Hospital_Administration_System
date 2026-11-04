package com.smart.schedule.service;

import com.smart.schedule.dto.specialization.SpecializationRequest;
import com.smart.schedule.dto.specialization.SpecializationResponse;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.SpecializationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpecializationServiceTest {

    @Mock
    private SpecializationRepository specializationRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @InjectMocks
    private SpecializationService specializationService;

    @Test
    void createSpecialization() {
        SpecializationRequest request = new SpecializationRequest(
                "Кардиология", "Диагностика и лечение заболеваний сердца");

        when(specializationRepository.existsByNameIgnoreCase("Кардиология")).thenReturn(false);
        when(specializationRepository.save(any(Specialization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SpecializationResponse response = specializationService.create(request);

        assertEquals("Кардиология", response.name());
        assertEquals("Диагностика и лечение заболеваний сердца", response.description());
        verify(specializationRepository).save(any(Specialization.class));
    }

    @Test
    void createSpecializationRejectsDuplicateName() {
        SpecializationRequest request = new SpecializationRequest("Кардиология", null);
        when(specializationRepository.existsByNameIgnoreCase("Кардиология")).thenReturn(true);

        assertThrows(ConflictException.class, () -> specializationService.create(request));
        verify(specializationRepository, never()).save(any(Specialization.class));
    }

    @Test
    void deleteSpecializationRejectsDoctorsInUse() {
        Specialization specialization = new Specialization("Кардиология", null);

        when(specializationRepository.findById(1L)).thenReturn(Optional.of(specialization));
        when(doctorRepository.existsBySpecializationId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> specializationService.delete(1L));
        verify(specializationRepository, never()).delete(any(Specialization.class));
    }

    @Test
    void updateSpecializationRejectsDuplicateName() {
        Specialization specialization = new Specialization("Кардиология", null);

        when(specializationRepository.findById(1L)).thenReturn(Optional.of(specialization));
        when(specializationRepository.existsByNameIgnoreCaseAndIdNot("Терапия", 1L))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> specializationService.update(
                1L, new SpecializationRequest("Терапия", null)));
    }
}
