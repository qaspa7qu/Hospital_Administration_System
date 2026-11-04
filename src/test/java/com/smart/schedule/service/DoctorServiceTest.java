package com.smart.schedule.service;

import com.smart.schedule.dto.doctor.CreateDoctorRequest;
import com.smart.schedule.dto.doctor.DoctorResponse;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Role;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SpecializationRepository specializationRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private WorkingScheduleRepository workingScheduleRepository;

    @InjectMocks
    private DoctorService doctorService;

    @Test
    void createDoctorWithDoctorRoleSucceeds() {
        User user = userWithRole(RoleName.DOCTOR);
        ReflectionTestUtils.setField(user, "id", 10L);
        Specialization specialization = specialization(20L, "Кардиология");

        CreateDoctorRequest request = new CreateDoctorRequest(
                10L, 20L, "Иван", "Иванов", "Иванович",
                "+7 900 000-00-00", "ivanov@example.com", "101",
                "Врач-кардиолог", true);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(doctorRepository.existsByUserId(10L)).thenReturn(false);
        when(specializationRepository.findById(20L)).thenReturn(Optional.of(specialization));
        when(doctorRepository.save(any(Doctor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DoctorResponse response = doctorService.create(request);

        assertEquals("Иван", response.firstName());
        assertEquals("Иванов", response.lastName());
        assertEquals(10L, response.userId());
        assertEquals(20L, response.specializationId());
        assertEquals("Кардиология", response.specializationName());
        assertEquals("Иванов Иван Иванович", response.fullName());
        verify(doctorRepository).save(any(Doctor.class));
    }

    @Test
    void createDoctorRejectsUserWithoutDoctorRole() {
        User user = userWithRole(RoleName.RECEPTIONIST);
        ReflectionTestUtils.setField(user, "id", 10L);

        CreateDoctorRequest request = new CreateDoctorRequest(
                10L, 20L, "Иван", "Иванов", null,
                null, null, null, null, true);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class, () -> doctorService.create(request));
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void createDoctorRejectsExistingProfile() {
        User user = userWithRole(RoleName.DOCTOR);
        ReflectionTestUtils.setField(user, "id", 10L);

        CreateDoctorRequest request = new CreateDoctorRequest(
                10L, 20L, "Иван", "Иванов", null,
                null, null, null, null, true);

        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(doctorRepository.existsByUserId(10L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> doctorService.create(request));
        verify(doctorRepository, never()).save(any(Doctor.class));
    }

    @Test
    void deleteDoctorRejectsExistingAppointments() {
        Doctor doctor = doctor(1L);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.existsByDoctorId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> doctorService.delete(1L));
        verify(workingScheduleRepository, never()).deleteByDoctorId(1L);
        verify(doctorRepository, never()).delete(any(Doctor.class));
    }

    @Test
    void deleteDoctorRemovesWorkingSchedules() {
        Doctor doctor = doctor(1L);

        when(doctorRepository.findById(1L)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.existsByDoctorId(1L)).thenReturn(false);

        doctorService.delete(1L);

        verify(workingScheduleRepository).deleteByDoctorId(1L);
        verify(doctorRepository).delete(doctor);
    }

    @Test
    void findByUsernameThrowsWhenProfileIsMissing() {
        User user = userWithRole(RoleName.DOCTOR);
        ReflectionTestUtils.setField(user, "id", 10L);

        when(userRepository.findByUsername("doctor.user")).thenReturn(Optional.of(user));
        when(doctorRepository.findByUserId(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> doctorService.findByUsername("doctor.user"));
    }

    private User userWithRole(RoleName roleName) {
        User user = new User("doctor.user", "hash", "Doctor User");
        user.addRole(new Role(roleName, roleName.name()));
        return user;
    }

    private Specialization specialization(Long id, String name) {
        Specialization specialization = new Specialization(name, null);
        ReflectionTestUtils.setField(specialization, "id", id);
        return specialization;
    }

    private Doctor doctor(Long id) {
        Doctor doctor = new Doctor(
                userWithRole(RoleName.DOCTOR),
                specialization(20L, "Кардиология"),
                "Иван",
                "Иванов");
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }
}
