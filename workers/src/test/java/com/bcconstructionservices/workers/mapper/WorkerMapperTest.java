package com.bcconstructionservices.workers.mapper;

import com.bcconstructionservices.user.service.UserLookupHelper;
import com.bcconstructionservices.workers.dto.WorkerCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerResponse;
import com.bcconstructionservices.workers.dto.WorkerUpdateRequest;
import com.bcconstructionservices.workers.entity.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerMapperTest {

    private WorkerMapper mapper;

    private UserLookupHelper userLookupHelper;

    @BeforeEach
    void setUp() {
        mapper = new WorkerMapperImpl();

        userLookupHelper = mock(UserLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);
    }

    private Worker buildWorker() {
        Worker worker = new Worker();
        worker.setId(1L);
        worker.setName("Ramon Villanueva");
        worker.setPosition("Mason");
        worker.setDailyRate(new BigDecimal("800.00"));
        worker.setActive(true);
        worker.setCreatedBy(1L);
        worker.setCreatedAt(Instant.parse("2026-09-01T09:15:30Z"));
        worker.setUpdatedAt(Instant.parse("2026-09-05T14:05:00Z"));
        return worker;
    }

    @Nested
    class ToResponse {

        @Test
        void shouldMapWorkerToResponseWithAllFields() {
            when(userLookupHelper.resolveUserName(1L)).thenReturn("Renzo Mendoza");
            Worker worker = buildWorker();

            WorkerResponse response = mapper.toResponse(worker);

            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("Ramon Villanueva");
            assertThat(response.getPosition()).isEqualTo("Mason");
            assertThat(response.getDailyRate()).isEqualByComparingTo("800.00");
            assertThat(response.isActive()).isTrue();
            assertThat(response.getCreatedBy()).isEqualTo(1L);
            assertThat(response.getCreatedByName()).isEqualTo("Renzo Mendoza");
            assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-09-01T09:15:30Z"));
            assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-09-05T14:05:00Z"));
        }
    }

    @Nested
    class ToEntity {

        @Test
        void shouldMapCreateRequestToEntityWithAllFields() {
            WorkerCreateRequest request = WorkerCreateRequest.builder()
                    .name("Ramon Villanueva")
                    .position("Mason")
                    .dailyRate(new BigDecimal("800.00"))
                    .build();

            Worker entity = mapper.toEntity(request);

            assertThat(entity.getName()).isEqualTo("Ramon Villanueva");
            assertThat(entity.getPosition()).isEqualTo("Mason");
            assertThat(entity.getDailyRate()).isEqualByComparingTo("800.00");
        }

        @Test
        void shouldNotSetServerManagedFields() {
            WorkerCreateRequest request = WorkerCreateRequest.builder()
                    .name("Ramon Villanueva")
                    .dailyRate(new BigDecimal("800.00"))
                    .build();

            Worker entity = mapper.toEntity(request);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getCreatedBy()).isNull();
            assertThat(entity.getCreatedAt()).isNull();
            assertThat(entity.getUpdatedAt()).isNull();
        }
    }

    @Nested
    class UpdateEntityFromRequest {

        @Test
        void shouldOverwriteFieldsWithRequestValues() {
            Worker existing = buildWorker();

            WorkerUpdateRequest request = WorkerUpdateRequest.builder()
                    .name("Updated Name")
                    .position("Foreman")
                    .dailyRate(new BigDecimal("950.00"))
                    .build();

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.getName()).isEqualTo("Updated Name");
            assertThat(existing.getPosition()).isEqualTo("Foreman");
            assertThat(existing.getDailyRate()).isEqualByComparingTo("950.00");
        }

        @Test
        void shouldNeverChangeActiveFlag() {
            Worker existing = buildWorker();
            existing.setActive(false);

            WorkerUpdateRequest request = WorkerUpdateRequest.builder()
                    .name("Updated Name")
                    .dailyRate(new BigDecimal("950.00"))
                    .build();

            mapper.updateEntityFromRequest(request, existing);

            assertThat(existing.isActive()).isFalse();
        }
    }
}
