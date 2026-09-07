package com.bcconstructionservices.workers.repository;

import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.workers.JpaAuditingTestConfig;
import com.bcconstructionservices.workers.entity.Attendance;
import com.bcconstructionservices.workers.entity.Worker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
class AttendanceRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private WorkerRepository workerRepository;

    private Worker worker;
    private Project project;

    @BeforeEach
    void setUp() {
        worker = Worker.builder().name("Ramon Villanueva").position("Mason").dailyRate(new BigDecimal("800.00")).build();
        entityManager.persist(worker);

        project = Project.builder().code("PRJ-TEST-1").name("Test Project").startDate(LocalDate.of(2026, 1, 1)).build();
        entityManager.persist(project);

        entityManager.flush();
    }

    private Attendance buildAttendance(LocalDate date) {
        return Attendance.builder()
                .worker(worker)
                .projectId(project.getId())
                .attendanceDate(date)
                .daysPresent(new BigDecimal("1.0"))
                .rateSnapshot(worker.getDailyRate())
                .build();
    }

    @Nested
    class UniqueWorkerDateConstraint {

        @Test
        void shouldRejectASecondAttendanceRecordForTheSameWorkerAndDate() {
            attendanceRepository.saveAndFlush(buildAttendance(LocalDate.of(2026, 9, 5)));

            assertThatExceptionOfType(DataIntegrityViolationException.class)
                    .isThrownBy(() -> attendanceRepository.saveAndFlush(buildAttendance(LocalDate.of(2026, 9, 5))));
        }
    }

    @Nested
    class ExistsByWorkerIdAndAttendanceDateTests {

        @Test
        void shouldReturnTrueWhenARecordAlreadyExists() {
            attendanceRepository.saveAndFlush(buildAttendance(LocalDate.of(2026, 9, 5)));

            assertThat(attendanceRepository.existsByWorkerIdAndAttendanceDate(worker.getId(), LocalDate.of(2026, 9, 5)))
                    .isTrue();
        }

        @Test
        void shouldReturnFalseWhenNoRecordExists() {
            assertThat(attendanceRepository.existsByWorkerIdAndAttendanceDate(worker.getId(), LocalDate.of(2026, 9, 5)))
                    .isFalse();
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldFilterByDateRange() {
            attendanceRepository.save(buildAttendance(LocalDate.of(2026, 9, 1)));
            attendanceRepository.save(buildAttendance(LocalDate.of(2026, 9, 10)));

            var page = attendanceRepository.search(null, null,
                    LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 15), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Attendance::getAttendanceDate)
                    .containsExactly(LocalDate.of(2026, 9, 10));
        }

        @Test
        void shouldFilterByWorkerAndProject() {
            attendanceRepository.save(buildAttendance(LocalDate.of(2026, 9, 1)));

            var page = attendanceRepository.search(worker.getId(), project.getId(), null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
        }
    }

    @Nested
    class CalendarSummaryTests {

        @Test
        void shouldGroupByDateAndProjectCountingDistinctWorkers() {
            Worker secondWorker = Worker.builder().name("Jun Santos").dailyRate(new BigDecimal("650.00")).build();
            entityManager.persist(secondWorker);
            entityManager.flush();

            attendanceRepository.save(buildAttendance(LocalDate.of(2026, 9, 1)));
            Attendance secondWorkerAttendance = Attendance.builder()
                    .worker(secondWorker)
                    .projectId(project.getId())
                    .attendanceDate(LocalDate.of(2026, 9, 1))
                    .daysPresent(new BigDecimal("1.0"))
                    .rateSnapshot(secondWorker.getDailyRate())
                    .build();
            attendanceRepository.save(secondWorkerAttendance);

            var rows = attendanceRepository.calendarSummary(null, null, null);

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getDate()).isEqualTo(LocalDate.of(2026, 9, 1));
            assertThat(rows.get(0).getProjectId()).isEqualTo(project.getId());
            assertThat(rows.get(0).getWorkerCount()).isEqualTo(2);
        }

        @Test
        void shouldFilterByProjectAndDateRange() {
            attendanceRepository.save(buildAttendance(LocalDate.of(2026, 9, 1)));
            attendanceRepository.save(buildAttendance(LocalDate.of(2026, 9, 20)));

            var rows = attendanceRepository.calendarSummary(
                    project.getId(), LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 25));

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        }

        @Test
        void shouldReturnEmptyWhenNothingRecorded() {
            var rows = attendanceRepository.calendarSummary(null, null, null);

            assertThat(rows).isEmpty();
        }
    }
}
