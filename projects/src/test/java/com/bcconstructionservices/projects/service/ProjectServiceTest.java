package com.bcconstructionservices.projects.service;

import com.bcconstructionservices.projects.dto.PageResponse;
import com.bcconstructionservices.projects.dto.ProjectCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectResponse;
import com.bcconstructionservices.projects.dto.ProjectUpdateRequest;
import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.entity.ProjectStatus;
import com.bcconstructionservices.projects.exception.DuplicateResourceException;
import com.bcconstructionservices.projects.exception.ProjectNotEditableException;
import com.bcconstructionservices.projects.exception.ResourceNotFoundException;
import com.bcconstructionservices.projects.mapper.ProjectMapperImpl;
import com.bcconstructionservices.projects.repository.ProjectRepository;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    private static final Long PROJECT_ID = 12L;

    @Mock
    private ProjectRepository projectRepository;
    @Spy
    private ProjectMapperImpl projectMapper = new ProjectMapperImpl();

    @InjectMocks
    private ProjectService projectService;

    @Mock
    private UserLookupHelper userLookupHelper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(projectMapper, "userLookupHelper", userLookupHelper);
    }

    // ---------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------

    private ProjectCreateRequest createRequest(String code) {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setCode(code);
        request.setName("Sta. Maria Warehouse Expansion");
        request.setStartDate(LocalDate.of(2026, 9, 1));
        return request;
    }

    private Project buildProject(ProjectStatus status) {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setCode("PRJ-2026-001");
        project.setName("Sta. Maria Warehouse Expansion");
        project.setStatus(status);
        project.setStartDate(LocalDate.of(2026, 9, 1));
        return project;
    }

    private void givenSavesEchoTheirArgument() {
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Project captureSavedProject() {
        ArgumentCaptor<Project> captor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------
    // createProject
    // ---------------------------------------------------------------

    @Nested
    class CreateProjectTests {

        @Test
        void shouldCreateProjectWhenCodeIsUnique() {
            when(projectRepository.existsByCode("PRJ-2026-001")).thenReturn(false);
            givenSavesEchoTheirArgument();

            projectService.createProject(createRequest("PRJ-2026-001"));

            Project saved = captureSavedProject();
            assertThat(saved.getCode()).isEqualTo("PRJ-2026-001");
            assertThat(saved.getName()).isEqualTo("Sta. Maria Warehouse Expansion");
        }

        @Test
        void shouldThrowDuplicateResourceExceptionWhenCodeAlreadyExists() {
            when(projectRepository.existsByCode("PRJ-2026-001")).thenReturn(true);

            assertThatExceptionOfType(DuplicateResourceException.class)
                    .isThrownBy(() -> projectService.createProject(createRequest("PRJ-2026-001")));

            verify(projectRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // updateProject
    // ---------------------------------------------------------------

    @Nested
    class UpdateProjectTests {

        private ProjectUpdateRequest updateRequest() {
            ProjectUpdateRequest request = new ProjectUpdateRequest();
            request.setName("Updated Name");
            request.setStartDate(LocalDate.of(2026, 9, 5));
            return request;
        }

        @Test
        void shouldUpdateProjectWhenActive() {
            Project existing = buildProject(ProjectStatus.ACTIVE);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));
            givenSavesEchoTheirArgument();

            projectService.updateProject(PROJECT_ID, updateRequest());

            assertThat(captureSavedProject().getName()).isEqualTo("Updated Name");
        }

        @Test
        void shouldUpdateProjectWhenOnHold() {
            Project existing = buildProject(ProjectStatus.ON_HOLD);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));
            givenSavesEchoTheirArgument();

            projectService.updateProject(PROJECT_ID, updateRequest());

            assertThat(captureSavedProject().getName()).isEqualTo("Updated Name");
        }

        @Test
        void shouldThrowProjectNotEditableExceptionWhenCompleted() {
            Project existing = buildProject(ProjectStatus.COMPLETED);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> projectService.updateProject(PROJECT_ID, updateRequest()))
                    .satisfies(ex -> {
                        assertThat(ex.getProjectId()).isEqualTo(PROJECT_ID);
                        assertThat(ex.getStatus()).isEqualTo(ProjectStatus.COMPLETED);
                    });

            verify(projectRepository, never()).save(any());
        }

        @Test
        void shouldThrowProjectNotEditableExceptionWhenCancelled() {
            Project existing = buildProject(ProjectStatus.CANCELLED);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> projectService.updateProject(PROJECT_ID, updateRequest()));

            verify(projectRepository, never()).save(any());
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenProjectDoesNotExist() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> projectService.updateProject(PROJECT_ID, updateRequest()));
        }
    }

    // ---------------------------------------------------------------
    // completeProject
    // ---------------------------------------------------------------

    @Nested
    class CompleteProjectTests {

        @Test
        void shouldTransitionActiveToCompleted() {
            Project existing = buildProject(ProjectStatus.ACTIVE);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));
            givenSavesEchoTheirArgument();

            projectService.completeProject(PROJECT_ID);

            assertThat(captureSavedProject().getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        }

        @Test
        void shouldTransitionOnHoldToCompleted() {
            Project existing = buildProject(ProjectStatus.ON_HOLD);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));
            givenSavesEchoTheirArgument();

            projectService.completeProject(PROJECT_ID);

            assertThat(captureSavedProject().getStatus()).isEqualTo(ProjectStatus.COMPLETED);
        }

        @Test
        void shouldThrowProjectNotEditableExceptionWhenAlreadyCompleted() {
            Project existing = buildProject(ProjectStatus.COMPLETED);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> projectService.completeProject(PROJECT_ID));

            verify(projectRepository, never()).save(any());
        }
    }

    // ---------------------------------------------------------------
    // requireEditableProject
    // ---------------------------------------------------------------

    @Nested
    class RequireEditableProjectTests {

        @Test
        void shouldReturnProjectWhenActive() {
            Project existing = buildProject(ProjectStatus.ACTIVE);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));

            Project result = projectService.requireEditableProject(PROJECT_ID);

            assertThat(result).isEqualTo(existing);
        }

        @Test
        void shouldThrowProjectNotEditableExceptionWhenCompleted() {
            Project existing = buildProject(ProjectStatus.COMPLETED);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(existing));

            assertThatExceptionOfType(ProjectNotEditableException.class)
                    .isThrownBy(() -> projectService.requireEditableProject(PROJECT_ID));
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenProjectDoesNotExist() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> projectService.requireEditableProject(PROJECT_ID));
        }
    }

    // ---------------------------------------------------------------
    // getById / search
    // ---------------------------------------------------------------

    @Nested
    class ReadMethodsTests {

        @Test
        void shouldReturnMappedResponseForExistingProject() {
            Project project = buildProject(ProjectStatus.ACTIVE);
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));

            ProjectResponse response = projectService.getById(PROJECT_ID);

            assertThat(response.getId()).isEqualTo(PROJECT_ID);
            assertThat(response.getCode()).isEqualTo("PRJ-2026-001");
        }

        @Test
        void shouldThrowResourceNotFoundExceptionWhenGetByIdMisses() {
            when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.empty());

            assertThatExceptionOfType(ResourceNotFoundException.class)
                    .isThrownBy(() -> projectService.getById(PROJECT_ID));
        }

        @Test
        void shouldDelegateSearchToRepositoryAndWrapInPageResponse() {
            Project project = buildProject(ProjectStatus.ACTIVE);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Project> page = new PageImpl<>(List.of(project), pageable, 1);
            when(projectRepository.search(ProjectStatus.ACTIVE, pageable)).thenReturn(page);

            PageResponse<ProjectResponse> result = projectService.search(ProjectStatus.ACTIVE, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }
}
