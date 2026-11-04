package com.smart.schedule.service;

import com.smart.schedule.dto.patient.PatientRequest;
import com.smart.schedule.dto.patient.PatientResponse;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;

    public PatientService(PatientRepository patientRepository,
                          AppointmentRepository appointmentRepository) {
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public List<PatientResponse> findAll(String search) {
        List<Patient> patients = (search == null || search.isBlank())
                ? patientRepository.findAllByOrderByLastNameAscFirstNameAsc()
                : patientRepository.findByLastNameContainingIgnoreCaseOrderByLastNameAsc(search.trim());

        return patients.stream()
                .map(PatientResponse::from)
                .toList();
    }

    public PatientResponse findById(Long id) {
        return PatientResponse.from(findEntity(id));
    }

    @Transactional
    public PatientResponse create(PatientRequest request) {
        Patient patient = new Patient(request.firstName(), request.lastName(), request.phone());
        applyRequest(patient, request);
        return PatientResponse.from(patientRepository.save(patient));
    }

    @Transactional
    public PatientResponse update(Long id, PatientRequest request) {
        Patient patient = findEntity(id);
        applyRequest(patient, request);
        return PatientResponse.from(patient);
    }

    @Transactional
    public void delete(Long id) {
        Patient patient = findEntity(id);
        if (appointmentRepository.existsByPatientId(id)) {
            throw new ConflictException("Cannot delete a patient with existing appointments");
        }
        patientRepository.delete(patient);
    }

    private Patient findEntity(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + id));
    }

    private void applyRequest(Patient patient, PatientRequest request) {
        patient.setFirstName(request.firstName());
        patient.setLastName(request.lastName());
        patient.setMiddleName(request.middleName());
        patient.setBirthDate(request.birthDate());
        patient.setPhone(request.phone());
        patient.setEmail(request.email());
        patient.setAddress(request.address());
        patient.setNotes(request.notes());
    }
}
