package com.smart.schedule;

import com.jayway.jsonpath.JsonPath;
import com.smart.schedule.entity.Doctor;
import com.smart.schedule.entity.Patient;
import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.Service;
import com.smart.schedule.entity.Specialization;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.WorkingSchedule;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.repository.DoctorRepository;
import com.smart.schedule.repository.PatientRepository;
import com.smart.schedule.repository.RoleRepository;
import com.smart.schedule.repository.ServiceRepository;
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

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class BillingReportApiIntegrationTests {

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
    private ServiceRepository serviceRepository;

    private Long doctorId;
    private Long secondDoctorId;
    private Long patientId;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        createUser("admin.billing", "Billing Admin", RoleName.ADMIN);
        createUser("receptionist.billing", "Billing Receptionist", RoleName.RECEPTIONIST);
        User doctorUser = createUser("doctor.billing", "Billing Doctor", RoleName.DOCTOR);
        User secondDoctorUser = createUser(
                "doctor.billing.second", "Billing Second Doctor", RoleName.DOCTOR);

        Specialization specialization = specializationRepository.save(
                new Specialization("Кардиология", "Сердечно-сосудистые заболевания"));

        Doctor doctor = doctorRepository.save(
                new Doctor(doctorUser, specialization, "Иван", "Иванов"));
        Doctor secondDoctor = doctorRepository.save(
                new Doctor(secondDoctorUser, specialization, "Пётр", "Сидоров"));

        WorkingSchedule schedule = new WorkingSchedule(
                doctor, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(17, 0));
        schedule.setSlotDurationMinutes(30);
        workingScheduleRepository.save(schedule);

        Patient patient = patientRepository.save(
                new Patient("Пётр", "Петров", "+7 900 000-00-00"));

        doctorId = doctor.getId();
        secondDoctorId = secondDoctor.getId();
        patientId = patient.getId();
        monday = LocalDate.now().plusWeeks(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    }

    @Test
    void adminManagesServiceCatalogAndRolesAreEnforced() throws Exception {
        String adminToken = login("admin.billing");
        String receptionistToken = login("receptionist.billing");
        String doctorToken = login("doctor.billing");

        long serviceId = createService(adminToken, "Консультация", "1500.00", 30, true);

        mockMvc.perform(get("/api/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Консультация"))
                .andExpect(jsonPath("$.price").value(closeTo(1500.00, 0.001)))
                .andExpect(jsonPath("$.durationMinutes").value(30))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody("консультация", "100.00", 30, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        mockMvc.perform(put("/api/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody("Консультация", "2000.00", 45, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(closeTo(2000.00, 0.001)))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/services")
                        .param("activeOnly", "false")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == %d)]".formatted(serviceId)).isNotEmpty());

        mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody("Процедура", "100.00", 15, true)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody("Отрицательная", "-10.00", 15, true)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/services/{id}", serviceId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void billingSnapshotsPricesRecalculatesTotalsAndFreezesTerminalAppointments()
            throws Exception {
        String token = login("admin.billing");

        long consultationId = createService(token, "Консультация", "1000.00", 30, true);
        long procedureId = createService(token, "Процедура", "500.50", 20, true);
        long freeId = createService(token, "Осмотр", "0.00", 10, true);
        long inactiveId = createService(token, "Архивная услуга", "100.00", 10, false);

        long appointmentId = createAppointment(token, monday, "10:00");

        mockMvc.perform(get("/api/appointments/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(closeTo(0.00, 0.001)));

        mockMvc.perform(post("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachBody(consultationId, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].unitPrice").value(closeTo(1000.00, 0.001)))
                .andExpect(jsonPath("$.services[0].lineTotal").value(closeTo(2000.00, 0.001)))
                .andExpect(jsonPath("$.totalPrice").value(closeTo(2000.00, 0.001)));

        mockMvc.perform(post("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachBody(procedureId, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(closeTo(2500.50, 0.001)));

        mockMvc.perform(post("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachBody(freeId, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.services.length()").value(3))
                .andExpect(jsonPath("$.totalPrice").value(closeTo(2500.50, 0.001)));

        long consultationItemId = itemIdForService(token, appointmentId, consultationId);

        mockMvc.perform(put("/api/appointments/{id}/services/{itemId}",
                        appointmentId, consultationItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(closeTo(3500.50, 0.001)));

        Service catalogService = serviceRepository.findById(consultationId).orElseThrow();
        catalogService.setPrice(new BigDecimal("9999.00"));
        serviceRepository.save(catalogService);

        mockMvc.perform(get("/api/appointments/{id}", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPrice").value(closeTo(3500.50, 0.001)));

        mockMvc.perform(get("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services[0].unitPrice").value(closeTo(1000.00, 0.001)))
                .andExpect(jsonPath("$.totalPrice").value(closeTo(3500.50, 0.001)));

        mockMvc.perform(post("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachBody(consultationId, 1)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachBody(inactiveId, 1)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/services/{id}", consultationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());

        long procedureItemId = itemIdForService(token, appointmentId, procedureId);

        mockMvc.perform(delete("/api/appointments/{id}/services/{itemId}",
                        appointmentId, procedureItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(2))
                .andExpect(jsonPath("$.totalPrice").value(closeTo(3000.00, 0.001)));

        mockMvc.perform(patch("/api/appointments/{id}/status", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(2));

        mockMvc.perform(post("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachBody(freeId, 1)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/appointments/{id}/services/{itemId}",
                        appointmentId, consultationItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":1}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/appointments/{id}/services/{itemId}",
                        appointmentId, consultationItemId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isConflict());
    }

    @Test
    void reportsAggregateStatsByPeriodAndDoctor() throws Exception {
        String token = login("admin.billing");

        long consultationId = createService(token, "Консультация", "1000.00", 30, true);

        long firstCompleted = createAppointment(token, monday, "09:00");
        attach(token, firstCompleted, consultationId, 2);
        complete(token, firstCompleted);

        long secondCompleted = createAppointment(token, monday, "09:30");
        attach(token, secondCompleted, consultationId, 1);
        complete(token, secondCompleted);

        long cancelled = createAppointment(token, monday, "10:00");
        attach(token, cancelled, consultationId, 1);
        mockMvc.perform(patch("/api/appointments/{id}/status", cancelled)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CANCELLED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/reports/appointments")
                        .param("from", monday.toString())
                        .param("to", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value(monday.toString()))
                .andExpect(jsonPath("$.to").value(monday.toString()))
                .andExpect(jsonPath("$.totalAppointments").value(3))
                .andExpect(jsonPath("$.scheduledAppointments").value(0))
                .andExpect(jsonPath("$.completedAppointments").value(2))
                .andExpect(jsonPath("$.cancelledAppointments").value(1))
                .andExpect(jsonPath("$.completedRevenue").value(closeTo(3000.00, 0.001)));

        mockMvc.perform(get("/api/reports/doctors")
                        .param("from", monday.toString())
                        .param("to", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors[?(@.doctorId == %d)].totalAppointments"
                        .formatted(doctorId)).value(hasItem(3)))
                .andExpect(jsonPath("$.doctors[?(@.doctorId == %d)].completedAppointments"
                        .formatted(doctorId)).value(hasItem(2)))
                .andExpect(jsonPath("$.doctors[?(@.doctorId == %d)].cancelledAppointments"
                        .formatted(doctorId)).value(hasItem(1)))
                .andExpect(jsonPath("$.doctors[?(@.doctorId == %d)].completedRevenue"
                        .formatted(doctorId)).value(hasItem(3000.00)))
                .andExpect(jsonPath("$.doctors[?(@.doctorId == %d)].totalAppointments"
                        .formatted(secondDoctorId)).value(hasItem(0)))
                .andExpect(jsonPath("$.doctors[?(@.doctorId == %d)].completedRevenue"
                        .formatted(secondDoctorId)).value(hasItem(0.00)));

        mockMvc.perform(get("/api/reports/doctors")
                        .param("from", monday.toString())
                        .param("to", monday.toString())
                        .param("doctorId", secondDoctorId.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors.length()").value(1))
                .andExpect(jsonPath("$.doctors[0].doctorId").value(secondDoctorId))
                .andExpect(jsonPath("$.doctors[0].totalAppointments").value(0))
                .andExpect(jsonPath("$.doctors[0].completedRevenue").value(closeTo(0.00, 0.001)));

        LocalDate emptyDate = monday.plusMonths(1);

        mockMvc.perform(get("/api/reports/appointments")
                        .param("from", emptyDate.toString())
                        .param("to", emptyDate.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAppointments").value(0))
                .andExpect(jsonPath("$.completedRevenue").value(closeTo(0.00, 0.001)));

        mockMvc.perform(get("/api/reports/appointments")
                        .param("from", monday.toString())
                        .param("to", monday.minusDays(1).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/reports/appointments")
                        .param("to", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());

        String receptionistToken = login("receptionist.billing");
        String doctorToken = login("doctor.billing");

        mockMvc.perform(get("/api/reports/appointments")
                        .param("from", monday.toString())
                        .param("to", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/reports/doctors")
                        .param("from", monday.toString())
                        .param("to", monday.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isForbidden());
    }

    private void attach(String token, long appointmentId, long serviceId, int quantity)
            throws Exception {
        mockMvc.perform(post("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachBody(serviceId, quantity)))
                .andExpect(status().isCreated());
    }

    private void complete(String token, long appointmentId) throws Exception {
        mockMvc.perform(patch("/api/appointments/{id}/status", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"COMPLETED"}
                                """))
                .andExpect(status().isOk());
    }

    private long itemIdForService(String token, long appointmentId, long serviceId)
            throws Exception {
        String body = mockMvc.perform(get("/api/appointments/{id}/services", appointmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Number> ids = JsonPath.read(
                body, "$.services[?(@.serviceId == %d)].id".formatted(serviceId));
        return ids.get(0).longValue();
    }

    private long createService(String token, String name, String price,
                               int durationMinutes, boolean active) throws Exception {
        return idOf(mockMvc.perform(post("/api/services")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(serviceBody(name, price, durationMinutes, active)))
                .andExpect(status().isCreated())
                .andReturn());
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

    private String serviceBody(String name, String price, int durationMinutes, boolean active) {
        return """
                {"name":"%s","description":"Описание","price":%s,
                 "durationMinutes":%d,"active":%s}
                """.formatted(name, price, durationMinutes, active);
    }

    private String attachBody(long serviceId, int quantity) {
        return """
                {"serviceId":%d,"quantity":%d}
                """.formatted(serviceId, quantity);
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
