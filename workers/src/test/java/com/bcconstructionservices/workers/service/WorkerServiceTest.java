package com.bcconstructionservices.workers.service;

import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.dto.WorkerCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerResponse;
import com.bcconstructionservices.workers.dto.WorkerUpdateRequest;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.exception.ResourceNotFoundException;
import com.bcconstructionservices.workers.mapper.WorkerMapper;
import com.bcconstructionservices.workers.repository.WorkerRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTest {

    private static final Long WORKER_ID = 1L;

    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private WorkerMapper workerMapper;

    @InjectMocks
    private WorkerService workerService;

    private Worker buildWorker() {
        Worker worker = new Worker();
        worker.setId(WORKER_ID);
        worker.setName("Ramon Villanueva");
        worker.setDailyRate(new BigDecimal("800.00"));
        worker.setActive(true);
        return worker;
    }

    @Nested
    class CreateWorkerTests {

        @Test
        void shouldMapAndSaveANewWorker() {
            WorkerCreateRequest request = WorkerCreateRequest.builder()
                    .name("Ramon Villanueva").dailyRate(new BigDecimal("800.00")).build();
            Worker entity = new Worker();
            Worker saved = buildWorker();
            WorkerResponse response = WorkerResponse.builder().id(WORKER_ID).build();

            when(workerMapper.toEntity(request)).thenReturn(entity);
            when(workerRepository.save(entity)).thenReturn(saved);
            when(workerMapper.toResponse(saved)).thenReturn(response);

            WorkerResponse result = workerService.createWorker(request);

            assertThat(result.getId()).isEqualTo(WORKER_ID);
        }
    }

    @Nested
    class UpdateWorkerTests {

        @Test
        void shouldUpdateAnExistingWorker() {
            Worker existing = buildWorker();
            WorkerUpdateRequest request = WorkerUpdateRequest.builder()
                    .name("Updated Name").dailyRate(new BigDecimal("950.00")).build();

            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(existing));
            when(workerRepository.save(existing)).thenReturn(existing);
            when(workerMapper.toResponse(existing)).thenReturn(WorkerResponse.builder().id(WORKER_ID).build());

            workerService.updateWorker(WORKER_ID, request);

            verify(workerMapper).updateEntityFromRequest(request, existing);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenWorkerDoesNotExist() {
            when(workerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> workerService.updateWorker(999L, WorkerUpdateRequest.builder().build()));
        }
    }

    @Nested
    class DeactivateWorkerTests {

        @Test
        void shouldSetActiveToFalse() {
            Worker existing = buildWorker();
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(existing));
            when(workerRepository.save(any(Worker.class))).thenAnswer(invocation -> invocation.getArgument(0));

            workerService.deactivateWorker(WORKER_ID);

            ArgumentCaptor<Worker> captor = ArgumentCaptor.forClass(Worker.class);
            verify(workerRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenWorkerDoesNotExist() {
            when(workerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> workerService.deactivateWorker(999L));
        }
    }

    @Nested
    class ReadMethodsTests {

        @Test
        void shouldReturnWorkerWhenFound() {
            Worker worker = buildWorker();
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker));
            when(workerMapper.toResponse(worker)).thenReturn(WorkerResponse.builder().id(WORKER_ID).build());

            WorkerResponse result = workerService.getById(WORKER_ID);

            assertThat(result.getId()).isEqualTo(WORKER_ID);
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenNotFound() {
            when(workerRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> workerService.getById(999L));
        }

        @Test
        void shouldReturnPagedResults() {
            Worker worker = buildWorker();
            Page<Worker> page = new PageImpl<>(List.of(worker));
            when(workerRepository.search(true, PageRequest.of(0, 10))).thenReturn(page);
            when(workerMapper.toResponse(worker)).thenReturn(WorkerResponse.builder().id(WORKER_ID).build());

            PageResponse<WorkerResponse> result = workerService.search(true, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }
    }
}
