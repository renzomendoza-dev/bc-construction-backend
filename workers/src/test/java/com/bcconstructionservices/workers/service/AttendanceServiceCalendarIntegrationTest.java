package com.bcconstructionservices.workers.service;

import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.repository.ProjectRepository;
import com.bcconstructionservices.user.service.UserLookupHelper;
import com.bcconstructionservices.workers.CrossModuleJpaRepositoriesTestConfig;
import com.bcconstructionservices.workers.WorkersTestApplication;
import com.bcconstructionservices.workers.dto.AttendanceCalendarEntry;
import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.repository.WorkerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real end-to-end reproduction for the GET /api/attendance/calendar 500 —
 * unlike every other test touching AttendanceService.getCalendar,
 * this boots a genuine Spring context (WorkersTestApplication, now scanning
 * com.bcconstructionservices.projects too) wiring the REAL ProjectLookupHelper/
 * ProjectExpenseService/ProjectRepository beans, not Mockito mocks and not
 * an EntityManager-only workaround. Written specifically to answer "does
 * calling the real code, real beans, real H2-backed data reproduce this
 * outside the live HTTP request path" — if this fails, the stack trace here
 * is ground truth for what's actually broken, replacing static-analysis
 * guessing with an actual reproduction.
 */
@SpringBootTest(classes = WorkersTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({AttendanceServiceCalendarIntegrationTest.StubUserLookupHelperConfig.class, CrossModuleJpaRepositoriesTestConfig.class})
@Transactional
class AttendanceServiceCalendarIntegrationTest {

    /**
     * The real UserLookupHelper lives in the user module, which this test
     * deliberately doesn't scan (it pulls in KeycloakAdminClient and other
     * beans needing Keycloak env config not present in this test context).
     * Every mapper here only calls it for createdByName/recordedByName —
     * cosmetic display fields unrelated to what this test is reproducing —
     * so a mock standing in for the bean is enough.
     */
    @TestConfiguration
    static class StubUserLookupHelperConfig {
        @Bean
        UserLookupHelper userLookupHelper() {
            return mock(UserLookupHelper.class);
        }
    }

    @Autowired
    private AttendanceService attendanceService;
    @Autowired
    private WorkerRepository workerRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void shouldReturnCalendarEntriesForARealAttendanceRecordWithoutThrowing() {
        Worker worker = workerRepository.save(Worker.builder()
                .name("Ramon Villanueva")
                .dailyRate(new BigDecimal("800.00"))
                .build());

        Project project = projectRepository.save(Project.builder()
                .code("PRJ-CAL-IT-1")
                .name("Calendar Integration Test Project")
                .startDate(LocalDate.of(2026, 1, 1))
                .build());

        attendanceService.createAttendance(AttendanceCreateRequest.builder()
                .workerId(worker.getId())
                .projectId(project.getId())
                .attendanceDate(LocalDate.of(2026, 9, 1))
                .daysPresent(new BigDecimal("1.0"))
                .notes("Integration test entry")
                .build());

        // The exact call GET /api/attendance/calendar makes, with real beans
        // all the way down — this is the actual reproduction attempt.
        List<AttendanceCalendarEntry> entries =
                attendanceService.getCalendar(project.getId(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getProjectId()).isEqualTo(project.getId());
        assertThat(entries.get(0).getProjectName()).isEqualTo("Calendar Integration Test Project");
        assertThat(entries.get(0).getWorkerCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyListWithoutThrowingWhenNoProjectIdFilterIsGiven() {
        // Exercises the null-projectId path through the real query + real
        // ProjectLookupHelper loop (which never runs when the list is empty,
        // but the query itself must still execute cleanly with a null bind).
        List<AttendanceCalendarEntry> entries = attendanceService.getCalendar(null, null, null);

        assertThat(entries).isNotNull();
    }
}
