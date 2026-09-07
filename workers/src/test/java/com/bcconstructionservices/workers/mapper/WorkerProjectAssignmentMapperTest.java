package com.bcconstructionservices.workers.mapper;

import com.bcconstructionservices.projects.service.ProjectLookupHelper;
import com.bcconstructionservices.user.service.UserLookupHelper;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentResponse;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.workers.entity.WorkerProjectAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerProjectAssignmentMapperTest {

    private WorkerProjectAssignmentMapper mapper;

    private UserLookupHelper userLookupHelper;
    private ProjectLookupHelper projectLookupHelper;

    @BeforeEach
    void setUp() {
        mapper = new WorkerProjectAssignmentMapperImpl();

        userLookupHelper = mock(UserLookupHelper.class);
        projectLookupHelper = mock(ProjectLookupHelper.class);
        ReflectionTestUtils.setField(mapper, "userLookupHelper", userLookupHelper);
        ReflectionTestUtils.setField(mapper, "projectLookupHelper", projectLookupHelper);
    }

    @Test
    void shouldMapAssignmentToResponseWithAllFields() {
        Worker worker = new Worker();
        worker.setId(1L);
        worker.setName("Ramon Villanueva");

        WorkerProjectAssignment assignment = new WorkerProjectAssignment();
        assignment.setId(21L);
        assignment.setWorker(worker);
        assignment.setProjectId(12L);
        assignment.setActive(true);
        assignment.setCreatedBy(1L);
        assignment.setCreatedAt(Instant.parse("2026-09-07T09:15:30Z"));
        assignment.setUpdatedAt(Instant.parse("2026-09-07T09:15:30Z"));

        when(projectLookupHelper.resolveProjectName(12L)).thenReturn("Sta. Maria Warehouse Expansion");
        when(userLookupHelper.resolveUserName(1L)).thenReturn("Renzo Mendoza");

        WorkerProjectAssignmentResponse response = mapper.toResponse(assignment);

        assertThat(response.getId()).isEqualTo(21L);
        assertThat(response.getWorkerId()).isEqualTo(1L);
        assertThat(response.getWorkerName()).isEqualTo("Ramon Villanueva");
        assertThat(response.getProjectId()).isEqualTo(12L);
        assertThat(response.getProjectName()).isEqualTo("Sta. Maria Warehouse Expansion");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getCreatedBy()).isEqualTo(1L);
        assertThat(response.getCreatedByName()).isEqualTo("Renzo Mendoza");
        assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-09-07T09:15:30Z"));
        assertThat(response.getUpdatedAt()).isEqualTo(Instant.parse("2026-09-07T09:15:30Z"));
    }
}
