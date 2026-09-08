package com.bcconstructionservices.workers.controller;

import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.repository.ProjectRepository;
import com.bcconstructionservices.user.service.UserLookupHelper;
import com.bcconstructionservices.workers.CrossModuleJpaRepositoriesTestConfig;
import com.bcconstructionservices.workers.WorkersTestApplication;
import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.repository.WorkerRepository;
import com.bcconstructionservices.workers.service.AttendanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The layer AttendanceServiceCalendarIntegrationTest doesn't reach: real
 * controller routing, real Jackson serialization of the response, the real
 * security filter chain, and the real WorkersExceptionHandler — all with
 * AttendanceService NOT mocked (unlike AttendanceControllerTest's @WebMvcTest
 * slice). If the service-level call is clean but this still 500s, the
 * failure is somewhere in this layer specifically.
 */
@SpringBootTest(classes = WorkersTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import({AttendanceCalendarHttpIntegrationTest.StubUserLookupHelperConfig.class, CrossModuleJpaRepositoriesTestConfig.class})
@Transactional
class AttendanceCalendarHttpIntegrationTest {

    @TestConfiguration
    static class StubUserLookupHelperConfig {
        @Bean
        UserLookupHelper userLookupHelper() {
            return mock(UserLookupHelper.class);
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private WorkerRepository workerRepository;
    @Autowired
    private ProjectRepository projectRepository;

    private static RequestPostProcessor authenticatedJwt(String... permissions) {
        List<GrantedAuthority> authorities = Arrays.stream(permissions)
                .map(p -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + p))
                .toList();
        return jwt().jwt(builder -> builder
                        .header("alg", "none")
                        .claim("sub", "keycloak-user-id"))
                .authorities(authorities);
    }

    @Test
    void shouldReturn200OverTheRealHttpStackWithNothingMocked() throws Exception {
        Worker worker = workerRepository.save(Worker.builder()
                .name("Ramon Villanueva")
                .dailyRate(new BigDecimal("800.00"))
                .build());

        Project project = projectRepository.save(Project.builder()
                .code("PRJ-CAL-HTTP-1")
                .name("Calendar HTTP Integration Test Project")
                .startDate(LocalDate.of(2026, 1, 1))
                .build());

        attendanceService.createAttendance(AttendanceCreateRequest.builder()
                .workerId(worker.getId())
                .projectId(project.getId())
                .attendanceDate(LocalDate.of(2026, 9, 1))
                .daysPresent(new BigDecimal("1.0"))
                .build());

        mockMvc.perform(get("/api/attendance/calendar")
                        .param("projectId", project.getId().toString())
                        .param("dateFrom", "2026-09-01")
                        .param("dateTo", "2026-09-30")
                        .with(authenticatedJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(project.getId()))
                .andExpect(jsonPath("$[0].workerCount").value(1));
    }

    @Test
    void shouldReturn200WithNoQueryParametersAtAll() throws Exception {
        // Mirrors GET /api/attendance/calendar with no params — all three
        // become null bind values in the repository query.
        mockMvc.perform(get("/api/attendance/calendar").with(authenticatedJwt()))
                .andExpect(status().isOk());
    }
}
