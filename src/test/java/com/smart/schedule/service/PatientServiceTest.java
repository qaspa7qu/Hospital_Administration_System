package com.smart.schedule.service;

import com.smart.schedule.dto.patient.PatientRequest;
import com.smart.schedule.dto.patient.PatientResponse;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.PatientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private PatientService patientService;

    @Test
    void createPatientMapsRequestToEntity() {
        PatientRequest request = new PatientRequest(
                "Пётр", "Петров", "Петрович",
                LocalDate.of(1990, 5, 20), "+7 900 000-00-00",
                "petr@example.com", "Москва", "Аллергия на пенициллин");

        when(patientRepository.save(any(Patient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PatientResponse response = patientService.create(request);

        assertEquals("Пётр", response.firstName());
        assertEquals("Петров", response.lastName());
        assertEquals("+7 900 000-00-00", response.phone());
        assertEquals(LocalDate.of(1990, 5, 20), response.birthDate());
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void findPatientThrowsWhenMissing() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> patientService.findById(99L));
    }

    @Test
    void updatePatientChangesAllFields() {
        Patient patient = new Patient("Пётр", "Петров", "+7 900 000-00-00");
        PatientRequest request = new PatientRequest(
                "Иван", "Иванов", "Иванович",
                LocalDate.of(1985, 1, 10), "+7 900 111-11-11",
                "ivan@example.com", "Санкт-Петербург", "Новая заметка");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));

        PatientResponse response = patientService.update(1L, request);

        assertEquals("Иван", response.firstName());
        assertEquals("Иванов", response.lastName());
        assertEquals("+7 900 111-11-11", response.phone());
        assertEquals("Новая заметка", response.notes());
    }

    @Test
    void deletePatientRejectsExistingAppointments() {
        Patient patient = new Patient("Пётр", "Петров", "+7 900 000-00-00");

        when(patientRepository.findById(1L)).thenReturn(Optional.of(patient));
        when(appointmentRepository.existsByPatientId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> patientService.delete(1L));
        verify(patientRepository, never()).delete(any(Patient.class));
    }

    @Test
    void findAllUsesSearchFilter() {
        Patient patient = new Patient("Пётр", "Петров", "+7 900 000-00-00");
        when(patientRepository.findByLastNameContainingIgnoreCaseOrderByLastNameAsc("Пет"))
                .thenReturn(List.of(patient));

        List<PatientResponse> result = patientService.findAll(" Пет ");

        assertEquals(1, result.size());
        assertEquals("Петров", result.getFirst().lastName());
    }
}
