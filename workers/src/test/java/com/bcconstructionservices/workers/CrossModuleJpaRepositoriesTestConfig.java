package com.bcconstructionservices.workers;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Widens JPA repository scanning to also pick up
 * com.bcconstructionservices.projects.repository (ProjectRepository,
 * ProjectExpenseRepository) for the handful of @SpringBootTest-based tests
 * that need real cross-module repository beans (see
 * AttendanceServiceCalendarIntegrationTest, AttendanceCalendarHttpIntegrationTest).
 *
 * Deliberately NOT on WorkersTestApplication itself: @EnableJpaRepositories
 * is unconditional (unlike Boot's auto-configured repository scanning, which
 * only activates when a DataSource/EntityManagerFactory bean exists), so
 * declaring it there fired for every test slice sharing that class —
 * including @WebMvcTest, which never wires an EntityManagerFactory — and
 * broke every @WebMvcTest in the module with NoSuchBeanDefinitionException:
 * No bean named 'entityManagerFactory' available. Import this explicitly
 * only where cross-module repositories are actually needed.
 *
 * Must list workers.repository alongside projects.repository: an explicit
 * @EnableJpaRepositories fully replaces Boot's auto-configured one rather
 * than adding to it, so omitting workers.repository here would silently stop
 * WorkerRepository/AttendanceRepository from being registered too.
 */
@TestConfiguration
@EnableJpaRepositories(basePackages = {
        "com.bcconstructionservices.workers.repository",
        "com.bcconstructionservices.projects.repository"
})
public class CrossModuleJpaRepositoriesTestConfig {
}
