package com.smart.schedule;

import com.smart.schedule.entity.Appointment;
import com.smart.schedule.entity.AppointmentService;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.Service;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.entity.enums.AppointmentStatus;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.PatientRepository;
import com.smart.schedule.repository.RoleRepository;
import com.smart.schedule.repository.ServiceRepository;
import com.smart.schedule.repository.SpecializationRepository;
import com.smart.schedule.repository.UserRepository;
import com.smart.schedule.repository.WorkingScheduleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClinicScheduleApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private WorkingScheduleRepository workingScheduleRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void contextLoadsAndPersistsEntityRelationships() {
        Role doctorRole = roleRepository.findByName(RoleName.DOCTOR).orElseThrow();

        User user = new User("doctor.ivanov", "$2a$10$test-hash", "Иванов Иван Иванович");
        user.setEmail("ivanov@clinic.local");
        user.addRole(doctorRole);
        userRepository.save(user);

        Specialization cardiology = specializationRepository.save(
                new Specialization("Кардиология", "Диагностика и лечение заболеваний сердца"));

        Doctor doctor = new Doctor(user, cardiology, "Иван", "Иванов");
        doctor.setMiddleName("Иванович");
        doctor.setOfficeNumber("101");
        doctorRepository.save(doctor);

        WorkingSchedule workingSchedule = new WorkingSchedule(
                doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0));
        workingSchedule.setSlotDurationMinutes(30);
        doctor.addWorkingSchedule(workingSchedule);
        workingScheduleRepository.save(workingSchedule);

        Patient patient = patientRepository.save(
                new Patient("Пётр", "Петров", "+7 (900) 000-00-00"));
        patient.setBirthDate(LocalDate.of(1990, 5, 20));

        Service consultation = serviceRepository.save(
                new Service("Консультация кардиолога", new BigDecimal("1500.00"), 30));

        Appointment appointment = new Appointment(
                patient, doctor, LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30));
        appointment.setReason("Плановый осмотр");
        appointment.addServiceItem(
                new AppointmentService(appointment, consultation, 2, consultation.getPrice()));
        appointmentRepository.save(appointment);

        entityManager.flush();
        entityManager.clear();

        Appointment saved = appointmentRepository.findById(appointment.getId()).orElseThrow();

        assertEquals(AppointmentStatus.SCHEDULED, saved.getStatus());
        assertEquals(1, saved.getServiceItems().size());
        assertEquals(new BigDecimal("3000.00"), saved.getTotalPrice());
        assertEquals("Кардиология", saved.getDoctor().getSpecialization().getName());
        assertTrue(userRepository.findByUsername("doctor.ivanov").isPresent());
        assertEquals(3, roleRepository.count());
    }
}
