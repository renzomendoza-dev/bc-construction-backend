package com.bcconstructionservices.workers.repository;

import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.workers.JpaAuditingTestConfig;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.entity.WorkerProjectAssignment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
class WorkerProjectAssignmentRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private WorkerProjectAssignmentRepository workerProjectAssignmentRepository;

    private Worker worker;
    private Project projectA;
    private Project projectB;

    @BeforeEach
    void setUp() {
        worker = Worker.builder().name("Ramon Villanueva").dailyRate(new BigDecimal("800.00")).build();
        entityManager.persist(worker);

        projectA = Project.builder().code("PRJ-TEST-A").name("Project A").startDate(LocalDate.of(2026, 1, 1)).build();
        entityManager.persist(projectA);

        projectB = Project.builder().code("PRJ-TEST-B").name("Project B").startDate(LocalDate.of(2026, 1, 1)).build();
        entityManager.persist(projectB);

        entityManager.flush();
    }

    private WorkerProjectAssignment buildAssignment(Project project, boolean active) {
        return WorkerProjectAssignment.builder().worker(worker).projectId(project.getId()).active(active).build();
    }

    /**
     * No DB-level partial unique index here (see V32's own comment on why —
     * H2's PostgreSQL-compatibility mode, used by this test suite, rejects
     * that syntax) — the DB happily allows two active rows for the same
     * worker; only WorkerProjectAssignmentService's existsByWorkerIdAndActiveTrue
     * pre-check (covered by WorkerProjectAssignmentServiceTest) prevents it.
     */
    @Nested
    class MultipleActiveAssignmentsAllowedAtTheDbLayer {

        @Test
        void shouldAllowTwoActiveAssignmentsForTheSameWorkerAtTheRepositoryLevel() {
            workerProjectAssignmentRepository.saveAndFlush(buildAssignment(projectA, true));

            assertThatCode(() -> workerProjectAssignmentRepository.saveAndFlush(buildAssignment(projectB, true)))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class ExistsByWorkerIdAndActiveTrueTests {

        @Test
        void shouldReturnTrueWhenWorkerHasAnActiveAssignment() {
            workerProjectAssignmentRepository.saveAndFlush(buildAssignment(projectA, true));

            assertThat(workerProjectAssignmentRepository.existsByWorkerIdAndActiveTrue(worker.getId())).isTrue();
        }

        @Test
        void shouldReturnFalseWhenWorkerHasNoActiveAssignment() {
            workerProjectAssignmentRepository.saveAndFlush(buildAssignment(projectA, false));

            assertThat(workerProjectAssignmentRepository.existsByWorkerIdAndActiveTrue(worker.getId())).isFalse();
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldFilterByProjectAndActive() {
            workerProjectAssignmentRepository.save(buildAssignment(projectA, true));

            var page = workerProjectAssignmentRepository.search(projectA.getId(), true, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
        }

        @Test
        void shouldReturnEmptyWhenFilteredToADifferentProject() {
            workerProjectAssignmentRepository.save(buildAssignment(projectA, true));

            var page = workerProjectAssignmentRepository.search(projectB.getId(), true, PageRequest.of(0, 10));

            assertThat(page.getContent()).isEmpty();
        }
    }
}
