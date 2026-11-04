package com.smart.schedule.service;

import com.smart.schedule.dto.doctor.CreateDoctorRequest;
import com.smart.schedule.dto.doctor.DoctorResponse;
import com.smart.schedule.dto.doctor.UpdateDoctorProfileRequest;
import com.smart.schedule.dto.doctor.UpdateDoctorRequest;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.exception.BadRequestException;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.exception.ResourceNotFoundException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.SpecializationRepository;
import com.smart.schedule.repository.UserRepository;
import com.smart.schedule.repository.WorkingScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final SpecializationRepository specializationRepository;
    private final AppointmentRepository appointmentRepository;
    private final WorkingScheduleRepository workingScheduleRepository;

    public DoctorService(
            DoctorRepository doctorRepository,
            UserRepository userRepository,
            SpecializationRepository specializationRepository,
            AppointmentRepository appointmentRepository,
            WorkingScheduleRepository workingScheduleRepository) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.specializationRepository = specializationRepository;
        this.appointmentRepository = appointmentRepository;
        this.workingScheduleRepository = workingScheduleRepository;
    }

    public List<DoctorResponse> findAll(Long specializationId) {
        List<Doctor> doctors = (specializationId == null)
                ? doctorRepository.findAllByOrderByLastNameAscFirstNameAsc()
                : doctorRepository.findBySpecializationIdOrderByLastNameAscFirstNameAsc(specializationId);

        return doctors.stream()
                .map(DoctorResponse::from)
                .toList();
    }

    public DoctorResponse findById(Long id) {
        return DoctorResponse.from(findEntity(id));
    }

    public DoctorResponse findByUsername(String username) {
        return DoctorResponse.from(findEntityByUsername(username));
    }

    @Transactional
    public DoctorResponse create(CreateDoctorRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + request.userId()));

        boolean hasDoctorRole = user.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.DOCTOR);
        if (!hasDoctorRole) {
            throw new BadRequestException("User must have the DOCTOR role");
        }
        if (doctorRepository.existsByUserId(request.userId())) {
            throw new ConflictException("A doctor profile already exists for this user");
        }

        Specialization specialization = findSpecialization(request.specializationId());
        Doctor doctor = new Doctor(user, specialization, request.firstName(), request.lastName());
        applyRequest(doctor, request);
        doctor.setActive(request.active() == null || request.active());

        return DoctorResponse.from(doctorRepository.save(doctor));
    }

    @Transactional
    public DoctorResponse update(Long id, UpdateDoctorRequest request) {
        Doctor doctor = findEntity(id);
        Specialization specialization = findSpecialization(request.specializationId());

        doctor.setSpecialization(specialization);
        applyRequest(doctor, request);
        if (request.active() != null) {
            doctor.setActive(request.active());
        }

        return DoctorResponse.from(doctor);
    }

    @Transactional
    public DoctorResponse updateOwnProfile(String username, UpdateDoctorProfileRequest request) {
        Doctor doctor = findEntityByUsername(username);

        doctor.setFirstName(request.firstName());
        doctor.setLastName(request.lastName());
        doctor.setMiddleName(request.middleName());
        doctor.setPhone(request.phone());
        doctor.setEmail(request.email());
        doctor.setOfficeNumber(request.officeNumber());
        doctor.setBio(request.bio());

        return DoctorResponse.from(doctor);
    }

    @Transactional
    public void delete(Long id) {
        Doctor doctor = findEntity(id);
        if (appointmentRepository.existsByDoctorId(id)) {
            throw new ConflictException("Cannot delete a doctor with existing appointments");
        }

        workingScheduleRepository.deleteByDoctorId(id);
        doctorRepository.delete(doctor);
    }

    private Doctor findEntity(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));
    }

    private Doctor findEntityByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        return doctorRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Doctor profile not found for user: " + username));
    }

    private Specialization findSpecialization(Long id) {
        return specializationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Specialization not found: " + id));
    }

    private void applyRequest(Doctor doctor, CreateDoctorRequest request) {
        doctor.setMiddleName(request.middleName());
        doctor.setPhone(request.phone());
        doctor.setEmail(request.email());
        doctor.setOfficeNumber(request.officeNumber());
        doctor.setBio(request.bio());
    }

    private void applyRequest(Doctor doctor, UpdateDoctorRequest request) {
        doctor.setFirstName(request.firstName());
        doctor.setLastName(request.lastName());
        doctor.setMiddleName(request.middleName());
        doctor.setPhone(request.phone());
        doctor.setEmail(request.email());
        doctor.setOfficeNumber(request.officeNumber());
        doctor.setBio(request.bio());
    }
}
