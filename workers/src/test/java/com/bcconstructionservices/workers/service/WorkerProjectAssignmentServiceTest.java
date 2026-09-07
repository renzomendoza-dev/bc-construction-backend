package com.bcconstructionservices.workers.service;

import com.bcconstructionservices.projects.dto.ProjectResponse;
import com.bcconstructionservices.projects.exception.ResourceNotFoundException;
import com.bcconstructionservices.projects.service.ProjectService;
import com.bcconstructionservices.workers.dto.PageResponse;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentResponse;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.entity.WorkerProjectAssignment;
import com.bcconstructionservices.workers.exception.DuplicateActiveAssignmentException;
import com.bcconstructionservices.workers.mapper.WorkerProjectAssignmentMapper;
import com.bcconstructionservices.workers.repository.WorkerProjectAssignmentRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerProjectAssignmentServiceTest {

    private static final Long WORKER_ID = 1L;
    private static final Long PROJECT_ID = 12L;
    private static final Long ASSIGNMENT_ID = 21L;

    @Mock
    private WorkerProjectAssignmentRepository workerProjectAssignmentRepository;
    @Mock
    private WorkerRepository workerRepository;
    @Mock
    private ProjectService projectService;
    @Mock
    private WorkerProjectAssignmentMapper workerProjectAssignmentMapper;

    @InjectMocks
    private WorkerProjectAssignmentService workerProjectAssignmentService;

    private Worker worker() {
        Worker worker = new Worker();
        worker.setId(WORKER_ID);
        worker.setName("Ramon Villanueva");
        return worker;
    }

    private WorkerProjectAssignmentCreateRequest request() {
        return WorkerProjectAssignmentCreateRequest.builder()
                .workerId(WORKER_ID).projectId(PROJECT_ID).build();
    }

    @Nested
    class AssignTests {

        @Test
        void shouldCreateAssignmentWhenWorkerHasNoActiveAssignment() {
            Worker worker = worker();
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker));
            when(projectService.getById(PROJECT_ID)).thenReturn(ProjectResponse.builder().id(PROJECT_ID).build());
            when(workerProjectAssignmentRepository.existsByWorkerIdAndActiveTrue(WORKER_ID)).thenReturn(false);
            when(workerProjectAssignmentRepository.save(any(WorkerProjectAssignment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(workerProjectAssignmentMapper.toResponse(any(WorkerProjectAssignment.class)))
                    .thenReturn(WorkerProjectAssignmentResponse.builder().id(ASSIGNMENT_ID).build());

            workerProjectAssignmentService.assign(request());

            ArgumentCaptor<WorkerProjectAssignment> captor = ArgumentCaptor.forClass(WorkerProjectAssignment.class);
            verify(workerProjectAssignmentRepository).save(captor.capture());
            assertThat(captor.getValue().getWorker()).isEqualTo(worker);
            assertThat(captor.getValue().getProjectId()).isEqualTo(PROJECT_ID);
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenWorkerDoesNotExist() {
            when(workerRepository.findById(999L)).thenReturn(Optional.empty());

            WorkerProjectAssignmentCreateRequest request = WorkerProjectAssignmentCreateRequest.builder()
                    .workerId(999L).projectId(PROJECT_ID).build();

            assertThatExceptionOfType(com.bcconstructionservices.workers.exception.ResourceNotFoundException.class)
                    .isThrownBy(() -> workerProjectAssignmentService.assign(request));
            verify(workerProjectAssignmentRepository, never()).save(any());
        }

        @Test
        void shouldPropagateResourceNotFoundExceptionWhenProjectDoesNotExist() {
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker()));
            when(projectService.getById(PROJECT_ID)).thenThrow(new ResourceNotFoundException("Project", PROJECT_ID));

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> workerProjectAssignmentService.assign(request()));
            verify(workerProjectAssignmentRepository, never()).save(any());
        }

        @Test
        void shouldThrowDuplicateActiveAssignmentExceptionWhenWorkerAlreadyHasOne() {
            when(workerRepository.findById(WORKER_ID)).thenReturn(Optional.of(worker()));
            when(projectService.getById(PROJECT_ID)).thenReturn(ProjectResponse.builder().id(PROJECT_ID).build());
            when(workerProjectAssignmentRepository.existsByWorkerIdAndActiveTrue(WORKER_ID)).thenReturn(true);

            assertThatExceptionOfType(DuplicateActiveAssignmentException.class)
                    .isThrownBy(() -> workerProjectAssignmentService.assign(request()));
            verify(workerProjectAssignmentRepository, never()).save(any());
        }
    }

    @Nested
    class DeactivateTests {

        @Test
        void shouldSetActiveToFalse() {
            WorkerProjectAssignment assignment = WorkerProjectAssignment.builder()
                    .id(ASSIGNMENT_ID).worker(worker()).projectId(PROJECT_ID).active(true).build();
            when(workerProjectAssignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
            when(workerProjectAssignmentRepository.save(any(WorkerProjectAssignment.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            workerProjectAssignmentService.deactivate(ASSIGNMENT_ID);

            ArgumentCaptor<WorkerProjectAssignment> captor = ArgumentCaptor.forClass(WorkerProjectAssignment.class);
            verify(workerProjectAssignmentRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isFalse();
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenAssignmentDoesNotExist() {
            when(workerProjectAssignmentRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatExceptionOfType(com.bcconstructionservices.workers.exception.ResourceNotFoundException.class)
                    .isThrownBy(() -> workerProjectAssignmentService.deactivate(999L));
        }
    }

    @Nested
    class ReadMethodsTests {

        @Test
        void shouldReturnPagedResults() {
            WorkerProjectAssignment assignment = WorkerProjectAssignment.builder()
                    .id(ASSIGNMENT_ID).worker(worker()).projectId(PROJECT_ID).build();
            Page<WorkerProjectAssignment> page = new PageImpl<>(List.of(assignment));
            when(workerProjectAssignmentRepository.search(PROJECT_ID, true, PageRequest.of(0, 10))).thenReturn(page);
            when(workerProjectAssignmentMapper.toResponse(assignment))
                    .thenReturn(WorkerProjectAssignmentResponse.builder().id(ASSIGNMENT_ID).build());

            PageResponse<WorkerProjectAssignmentResponse> result =
                    workerProjectAssignmentService.search(PROJECT_ID, true, PageRequest.of(0, 10));

            assertThat(result.getContent()).hasSize(1);
        }
    }
}
