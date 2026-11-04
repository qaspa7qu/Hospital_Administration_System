package com.smart.schedule;

import com.smart.schedule.dto.appointment.AppointmentRequest;
import com.smart.schedule.dto.appointment.AppointmentResponse;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.exception.ConflictException;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.PatientRepository;
import com.smart.schedule.repository.RoleRepository;
import com.smart.schedule.repository.SpecializationRepository;
import com.smart.schedule.repository.UserRepository;
import com.smart.schedule.repository.WorkingScheduleRepository;
import com.smart.schedule.service.AppointmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AppointmentConcurrencyIntegrationTests {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SpecializationRepository specializationRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private WorkingScheduleRepository workingScheduleRepository;

    private Long userId;
    private Long specializationId;
    private Long doctorId;
    private Long patientId;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Role doctorRole = roleRepository.findByName(RoleName.DOCTOR).orElseThrow();
        User user = new User(
                "concurrent.doctor." + suffix,
                passwordEncoder.encode("Password123!"),
                "Concurrent Doctor");
        user.addRole(doctorRole);
        user = userRepository.save(user);
        userId = user.getId();

        Specialization specialization = specializationRepository.save(
                new Specialization("Concurrency " + suffix, null));
        specializationId = specialization.getId();

        Doctor doctor = doctorRepository.save(
                new Doctor(user, specialization, "Иван", "Иванов"));
        doctorId = doctor.getId();

        WorkingSchedule schedule = new WorkingSchedule(
                doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        schedule.setSlotDurationMinutes(30);
        workingScheduleRepository.save(schedule);

        Patient patient = patientRepository.save(
                new Patient("Пётр", "Петров", "+7 900 000-00-00"));
        patientId = patient.getId();

        monday = LocalDate.now().plusWeeks(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @AfterEach
    void tearDown() {
        if (doctorId != null) {
            appointmentRepository.deleteAll(
                    appointmentRepository
                            .findByDoctorIdOrderByAppointmentDateDescStartTimeDesc(doctorId));
            workingScheduleRepository.deleteAll(
                    workingScheduleRepository
                            .findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId));
            doctorRepository.deleteById(doctorId);
        }
        if (patientId != null) {
            patientRepository.deleteById(patientId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
        if (specializationId != null) {
            specializationRepository.deleteById(specializationId);
        }
    }

    @Test
    void onlyOneOfTwoConcurrentRequestsForSameSlotSucceeds() throws Exception {
        AppointmentRequest request = request(LocalTime.of(10, 0));

        List<Object> results = runConcurrently(
                () -> appointmentService.create(request),
                () -> appointmentService.create(request));

        assertEquals(1, results.stream()
                .filter(result -> result instanceof AppointmentResponse)
                .count(), "Exactly one booking must succeed");
        assertEquals(1, results.stream()
                .filter(result -> result instanceof ConflictException)
                .count(), "The second request must get a conflict");
        assertEquals(1, appointmentRepository
                .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(doctorId, monday)
                .size(), "Only one appointment must be persisted");
    }

    @Test
    void concurrentRequestsForDifferentSlotsBothSucceed() throws Exception {
        List<Object> results = runConcurrently(
                () -> appointmentService.create(request(LocalTime.of(10, 0))),
                () -> appointmentService.create(request(LocalTime.of(10, 30))));

        assertTrue(results.stream().allMatch(result -> result instanceof AppointmentResponse),
                "Non-overlapping slots must both be booked");
        assertEquals(2, appointmentRepository
                .findByDoctorIdAndAppointmentDateOrderByStartTimeAsc(doctorId, monday)
                .size());
    }

    @Test
    void concurrentPartiallyOverlappingRequestsProduceOneConflict() throws Exception {
        List<Object> results = runConcurrently(
                () -> appointmentService.create(request(LocalTime.of(10, 0))),
                () -> appointmentService.create(request(LocalTime.of(10, 15))));

        assertEquals(1, results.stream()
                .filter(result -> result instanceof AppointmentResponse)
                .count());
        assertEquals(1, results.stream()
                .filter(result -> result instanceof ConflictException)
                .count());
    }

    private List<Object> runConcurrently(Callable<Object> first, Callable<Object> second)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Object> firstFuture = executor.submit(guarded(first, ready, start));
            Future<Object> secondFuture = executor.submit(guarded(second, ready, start));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Workers did not become ready");
            start.countDown();

            return List.of(
                    firstFuture.get(30, TimeUnit.SECONDS),
                    secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Object> guarded(Callable<Object> action,
                                     CountDownLatch ready,
                                     CountDownLatch start) {
        return () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS), "Start signal was not received");
            try {
                return action.call();
            } catch (RuntimeException exception) {
                return exception;
            }
        };
    }

    private AppointmentRequest request(LocalTime startTime) {
        return new AppointmentRequest(
                patientId, doctorId, monday, startTime,
                "Консультация", "Параллельное бронирование");
    }
}
