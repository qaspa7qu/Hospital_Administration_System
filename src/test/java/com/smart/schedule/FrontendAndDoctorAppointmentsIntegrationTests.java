package com.smart.schedule;

import com.smart.schedule.entity.Appointment;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.entity.enums.AppointmentStatus;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.repository.AppointmentRepository;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.PatientRepository;
import com.smart.schedule.repository.RoleRepository;
import com.smart.schedule.repository.SpecializationRepository;
import com.smart.schedule.repository.UserRepository;
import com.smart.schedule.repository.WorkingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FrontendAndDoctorAppointmentsIntegrationTests {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

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

    @Autowired
    private AppointmentRepository appointmentRepository;

    private Doctor doctor;
    private Doctor secondDoctor;
    private Patient patient;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        createUser("admin.stage6", "Stage Six Admin", RoleName.ADMIN);
        createUser("receptionist.stage6", "Stage Six Receptionist", RoleName.RECEPTIONIST);
        User doctorUser = createUser("doctor.stage6", "Stage Six Doctor", RoleName.DOCTOR);
        User secondDoctorUser = createUser(
                "doctor.stage6.second", "Stage Six Second Doctor", RoleName.DOCTOR);
        createUser("doctor.stage6.noprofile", "Stage Six No Profile", RoleName.DOCTOR);

        Specialization specialization = specializationRepository.save(
                new Specialization("Неврология", "Заболевания нервной системы"));

        doctor = doctorRepository.save(
                new Doctor(doctorUser, specialization, "Иван", "Иванов"));
        secondDoctor = doctorRepository.save(
                new Doctor(secondDoctorUser, specialization, "Пётр", "Сидоров"));

        WorkingSchedule firstSchedule = new WorkingSchedule(
                doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        firstSchedule.setSlotDurationMinutes(30);
        workingScheduleRepository.save(firstSchedule);

        WorkingSchedule secondSchedule = new WorkingSchedule(
                secondDoctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        secondSchedule.setSlotDurationMinutes(30);
        workingScheduleRepository.save(secondSchedule);

        patient = patientRepository.save(
                new Patient("Пётр", "Петров", "+7 900 111-22-33"));

        monday = LocalDate.now().plusWeeks(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @Test
    void staticFrontendIsPubliclyServed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .forwardedUrl("index.html"));

        byte[] indexHtmlBytes = mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"login-screen\"")))
                .andExpect(content().string(containsString("/js/app.js")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String indexHtml = new String(indexHtmlBytes, StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(
                indexHtml.contains("МедРасписание"),
                "index.html must contain the localized application name");

        mockMvc.perform(get("/css/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--teal-600")))
                .andExpect(content().string(containsString(".calendar-grid")));

        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NAV_GROUPS")));

        mockMvc.perform(get("/js/views/calendar.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("renderDayPanel")));

        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isOk());
    }

    @Test
    void doctorReadsOnlyOwnAppointmentsInDescendingOrder() throws Exception {
        Appointment first = createAppointment(doctor, monday, "10:00");
        Appointment second = createAppointment(doctor, monday, "11:00");
        Appointment otherDoctor = createAppointment(secondDoctor, monday, "12:00");

        String token = login("doctor.stage6");

        mockMvc.perform(get("/api/doctor/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[0].startTime").value("11:00:00"))
                .andExpect(jsonPath("$[1].id").value(first.getId()))
                .andExpect(jsonPath("$[1].startTime").value("10:00:00"))
                .andExpect(jsonPath("$[*].doctorId",
                        everyItem(is(doctor.getId().intValue()))))
                .andExpect(jsonPath("$[*].id",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(
                                otherDoctor.getId().intValue()))));
    }

    @Test
    void doctorAppointmentRangeIsInclusiveAndKeepsAllStatuses() throws Exception {
        Appointment mondayScheduled = createAppointment(doctor, monday, "10:00");
        Appointment mondayCancelled = createAppointment(doctor, monday, "11:00");
        mondayCancelled.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(mondayCancelled);
        Appointment nextMonday = createAppointment(doctor, monday.plusWeeks(1), "10:00");

        String token = login("doctor.stage6");

        mockMvc.perform(get("/api/doctor/appointments")
                        .param("from", monday.toString())
                        .param("to", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(mondayScheduled.getId()))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[1].id").value(mondayCancelled.getId()))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));

        mockMvc.perform(get("/api/doctor/appointments")
                        .param("from", monday.plusDays(1).toString())
                        .param("to", monday.plusDays(6).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/doctor/appointments")
                        .param("from", monday.toString())
                        .param("to", monday.plusWeeks(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].id").value(nextMonday.getId()));
    }

    @Test
    void doctorAppointmentDatesAndRolesAreValidated() throws Exception {
        String doctorToken = login("doctor.stage6");
        String adminToken = login("admin.stage6");
        String receptionistToken = login("receptionist.stage6");

        mockMvc.perform(get("/api/doctor/appointments")
                        .param("from", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Both 'from' and 'to' dates are required"));

        mockMvc.perform(get("/api/doctor/appointments")
                        .param("to", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Both 'from' and 'to' dates are required"));

        mockMvc.perform(get("/api/doctor/appointments")
                        .param("from", monday.plusDays(1).toString())
                        .param("to", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("'from' date must not be after 'to' date"));

        mockMvc.perform(get("/api/doctor/appointments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/doctor/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/doctor/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken)))
                .andExpect(status().isForbidden());

        String noProfileToken = login("doctor.stage6.noprofile");
        mockMvc.perform(get("/api/doctor/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(noProfileToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("Doctor profile not found for user: doctor.stage6.noprofile"));
    }

    private Appointment createAppointment(Doctor owner, LocalDate date, String startTime) {
        LocalTime start = LocalTime.parse(startTime);
        Appointment appointment = new Appointment(
                patient, owner, date, start, start.plusMinutes(30));
        appointment.setReason("Консультация");
        return appointmentRepository.save(appointment);
    }

    private User createUser(String username, String fullName, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(username, passwordEncoder.encode(PASSWORD), fullName);
        user.addRole(role);
        return userRepository.save(user);
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, PASSWORD).trim()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return com.jayway.jsonpath.JsonPath.read(response, "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
