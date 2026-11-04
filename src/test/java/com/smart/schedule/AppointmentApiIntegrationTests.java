package com.smart.schedule;

import com.jayway.jsonpath.JsonPath;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.entity.enums.RoleName;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AppointmentApiIntegrationTests {

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

    private Long doctorId;
    private Long patientId;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        createUser("admin.appointment", "Appointment Admin", RoleName.ADMIN);
        createUser("receptionist.appointment", "Appointment Receptionist", RoleName.RECEPTIONIST);
        User doctorUser = createUser("doctor.appointment", "Appointment Doctor", RoleName.DOCTOR);

        Specialization specialization = specializationRepository.save(
                new Specialization("Кардиология", "Сердечно-сосудистые заболевания"));
        Doctor doctor = doctorRepository.save(
                new Doctor(doctorUser, specialization, "Иван", "Иванов"));
        doctor.setMiddleName("Иванович");
        doctorRepository.save(doctor);

        WorkingSchedule schedule = new WorkingSchedule(
                doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        schedule.setSlotDurationMinutes(30);
        workingScheduleRepository.save(schedule);

        Patient patient = patientRepository.save(
                new Patient("Пётр", "Петров", "+7 900 000-00-00"));

        doctorId = doctor.getId();
        patientId = patient.getId();
        monday = LocalDate.now().plusWeeks(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @Test
    void adminCreatesAppointmentAndSeesComputedEndTime() throws Exception {
        String token = login("admin.appointment");

        long appointmentId = idOf(mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "10:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(patientId))
                .andExpect(jsonPath("$.doctorId").value(doctorId))
                .andExpect(jsonPath("$.appointmentDate").value(monday.toString()))
                .andExpect(jsonPath("$.startTime").value("10:00:00"))
                .andExpect(jsonPath("$.endTime").value("10:30:00"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.patientFullName").value("Петров Пётр"))
                .andExpect(jsonPath("$.doctorFullName").value("Иванов Иван Иванович"))
                .andReturn());

        mockMvc.perform(get("/api/appointments/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointmentId));

        mockMvc.perform(get("/api/appointments")
                        .param("doctorId", doctorId.toString())
                        .param("date", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void duplicateSlotReturnsConflict() throws Exception {
        String token = login("admin.appointment");
        createAppointment(token, monday, "10:00");

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "10:00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void partiallyOverlappingSlotReturnsConflictAndAdjacentSlotIsAllowed() throws Exception {
        String token = login("admin.appointment");
        createAppointment(token, monday, "10:00");

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "10:15")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "10:30")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("10:30:00"))
                .andExpect(jsonPath("$.endTime").value("11:00:00"));
    }

    @Test
    void appointmentOutsideWorkingHoursReturnsConflict() throws Exception {
        String token = login("admin.appointment");

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "18:00")))
                .andExpect(status().isConflict());
    }

    @Test
    void appointmentOnNonWorkingDayReturnsConflict() throws Exception {
        String token = login("admin.appointment");

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday.plusDays(1), "10:00")))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellationReleasesSlotForRebooking() throws Exception {
        String token = login("admin.appointment");
        long appointmentId = createAppointment(token, monday, "10:00");

        mockMvc.perform(patch("/api/appointments/{id}/status", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "10:00")))
                .andExpect(status().isCreated());
    }

    @Test
    void completedAppointmentCannotBeCancelledOrRescheduled() throws Exception {
        String token = login("admin.appointment");
        long appointmentId = createAppointment(token, monday, "10:00");

        mockMvc.perform(patch("/api/appointments/{id}/status", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(patch("/api/appointments/{id}/status", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/appointments/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "11:00")))
                .andExpect(status().isConflict());
    }

    @Test
    void rescheduleMovesAppointmentAndChecksOverlap() throws Exception {
        String token = login("admin.appointment");
        long firstId = createAppointment(token, monday, "10:00");
        createAppointment(token, monday, "11:00");

        mockMvc.perform(put("/api/appointments/{id}", firstId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "11:00")))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/appointments/{id}", firstId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "12:00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startTime").value("12:00:00"))
                .andExpect(jsonPath("$.endTime").value("12:30:00"));

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "10:00")))
                .andExpect(status().isCreated());
    }

    @Test
    void receptionistCanManageAppointmentsButDoctorCannot() throws Exception {
        String receptionistToken = login("receptionist.appointment");
        String doctorToken = login("doctor.appointment");

        createAppointment(receptionistToken, monday, "10:00");

        mockMvc.perform(get("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(monday, "11:00")))
                .andExpect(status().isForbidden());
    }

    private long createAppointment(String token, LocalDate date, String startTime)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/appointments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appointmentBody(date, startTime)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String appointmentBody(LocalDate date, String startTime) {
        return """
                {"patientId":%d,"doctorId":%d,"appointmentDate":"%s",
                 "startTime":"%s","reason":"Консультация","notes":"Первичный приём"}
                """.formatted(patientId, doctorId, date, startTime);
    }

    private User createUser(String username, String fullName, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(username, passwordEncoder.encode(PASSWORD), fullName);
        user.addRole(role);
        return userRepository.save(user);
    }

    private long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
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

        return JsonPath.read(response, "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
