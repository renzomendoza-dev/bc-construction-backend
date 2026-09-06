package com.bcconstructionservices.workers.repository;

import com.bcconstructionservices.workers.JpaAuditingTestConfig;
import com.bcconstructionservices.workers.entity.Worker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
class WorkerRepositoryTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private WorkerRepository workerRepository;

    private Worker buildWorker(String name, boolean active) {
        return Worker.builder().name(name).position("Mason").dailyRate(new BigDecimal("800.00")).active(active).build();
    }

    @Nested
    class DefaultActive {

        @Test
        void shouldDefaultActiveToTrue() {
            Worker worker = new Worker();
            worker.setName("Ramon Villanueva");
            worker.setDailyRate(new BigDecimal("750.00"));

            Worker saved = workerRepository.saveAndFlush(worker);
            entityManager.clear();

            Worker reloaded = workerRepository.findById(saved.getId()).orElseThrow();
            assertThat(reloaded.isActive()).isTrue();
        }
    }

    @Nested
    class SearchTests {

        @Test
        void shouldFilterByActive() {
            workerRepository.save(buildWorker("Active Worker", true));
            workerRepository.save(buildWorker("Retired Worker", false));

            var activeOnly = workerRepository.search(true, PageRequest.of(0, 10));

            assertThat(activeOnly.getContent()).extracting(Worker::getName).containsExactly("Active Worker");
        }

        @Test
        void shouldReturnAllWhenActiveFilterIsNull() {
            workerRepository.save(buildWorker("Active Worker", true));
            workerRepository.save(buildWorker("Retired Worker", false));

            var all = workerRepository.search(null, PageRequest.of(0, 10));

            assertThat(all.getContent()).hasSize(2);
        }
    }
}
