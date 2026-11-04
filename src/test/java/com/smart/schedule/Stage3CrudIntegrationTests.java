package com.smart.schedule;

import com.jayway.jsonpath.JsonPath;
import com.smart.schedule.entity.Role;
import com.smart.schedule.entity.User;
import com.smart.schedule.entity.enums.RoleName;
import com.smart.schedule.repository.RoleRepository;
import com.smart.schedule.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class Stage3CrudIntegrationTests {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        createUser("admin.crud", "CRUD Admin", RoleName.ADMIN);
        createUser("receptionist.crud", "CRUD Receptionist", RoleName.RECEPTIONIST);
    }

    @Test
    void adminCanManageFullCrudScenarioAndDoctorReadsOwnData() throws Exception {
        String adminToken = login("admin.crud");

        long specializationId = idOf(mockMvc.perform(post("/api/specializations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Кардиология","description":"Диагностика и лечение сердца"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Кардиология"))
                .andReturn());

        long doctorUserId = idOf(mockMvc.perform(post("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"doctor.crud","password":"Password123!",
                                 "fullName":"Иванов Иван","email":"doctor@example.com",
                                 "roles":["DOCTOR"]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("DOCTOR"))
                .andReturn());

        long doctorId = idOf(mockMvc.perform(post("/api/doctors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":%d,"specializationId":%d,
                                 "firstName":"Иван","lastName":"Иванов","middleName":"Иванович",
                                 "phone":"+7 900 000-00-00","officeNumber":"101"}
                                """.formatted(doctorUserId, specializationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(doctorUserId))
                .andExpect(jsonPath("$.specializationName").value("Кардиология"))
                .andReturn());

        String scheduleBody = """
                {"dayOfWeek":"MONDAY","startTime":"09:00","endTime":"17:00",
                 "slotDurationMinutes":30,"active":true}
                """;

        long scheduleId = idOf(mockMvc.perform(post("/api/doctors/{doctorId}/schedule", doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doctorId").value(doctorId))
                .andExpect(jsonPath("$.dayOfWeek").value("MONDAY"))
                .andReturn());

        mockMvc.perform(post("/api/doctors/{doctorId}/schedule", doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        long patientId = idOf(mockMvc.perform(post("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Пётр","lastName":"Петров","phone":"+7 900 111-11-11",
                                 "birthDate":"1990-05-20","notes":"Первичный приём"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastName").value("Петров"))
                .andReturn());

        String doctorToken = login("doctor.crud");

        mockMvc.perform(get("/api/doctor/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("doctor.crud"))
                .andExpect(jsonPath("$.specializationName").value("Кардиология"));

        mockMvc.perform(get("/api/doctor/schedule")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(scheduleId))
                .andExpect(jsonPath("$[0].startTime").value("09:00:00"));

        mockMvc.perform(put("/api/doctor/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Иван","lastName":"Иванов","middleName":"Иванович",
                                 "phone":"+7 900 222-22-22","officeNumber":"202"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officeNumber").value("202"));

        mockMvc.perform(get("/api/doctors")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(delete("/api/doctors/{doctorId}/schedule/{scheduleId}",
                                doctorId, scheduleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/doctors/{doctorId}/schedule", doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(delete("/api/doctors/{doctorId}", doctorId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/specializations/{id}", specializationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/admin/users/{id}", doctorUserId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/patients/{id}", patientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    void doctorCannotAccessPatients() throws Exception {
        createUser("doctor.denied", "Denied Doctor", RoleName.DOCTOR);
        String doctorToken = login("doctor.denied");

        mockMvc.perform(get("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(doctorToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void receptionistCanManagePatientsButCannotManageSpecializations() throws Exception {
        String receptionistToken = login("receptionist.crud");

        long patientId = idOf(mockMvc.perform(post("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Анна","lastName":"Сидорова","phone":"+7 900 333-33-33"}
                                """))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(put("/api/patients/{id}", patientId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Анна","lastName":"Сидорова-Новая",
                                 "phone":"+7 900 444-44-44","notes":"Обновлено"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Сидорова-Новая"));

        mockMvc.perform(get("/api/patients")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(post("/api/specializations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(receptionistToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Терапия"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private void createUser(String username, String fullName, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User(username, passwordEncoder.encode(PASSWORD), fullName);
        user.addRole(role);
        userRepository.save(user);
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
