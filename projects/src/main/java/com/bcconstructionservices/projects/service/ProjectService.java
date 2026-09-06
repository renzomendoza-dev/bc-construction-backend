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
import com.bcconstructionservices.projects.mapper.ProjectMapper;
import com.bcconstructionservices.projects.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for projects — the aggregation root whose running expense is
 * tracked via ProjectExpenseService/ProjectExpense. Deliberately independent
 * of every other module (see Project's own javadoc for why).
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request) {
        // toEntity only covers name/description/budget/startDate/endDate —
        // status/initiatedBy/createdAt/updatedAt are all ignore=true by
        // design (see the mapper's javadoc). code is a genuine 1:1 copy, so
        // it IS covered by the mapper — checked for uniqueness here first,
        // since that's not mapper territory.
        if (projectRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Project", "code", request.getCode());
        }

        Project project = projectMapper.toEntity(request);

        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(saved);
    }

    /**
     * Full-replacement update, only while ACTIVE/ON_HOLD (422 otherwise) —
     * matches MaterialRequestService.update's shape: fields copied as given
     * (including null, clearing them). code/status are untouched here.
     */
    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectUpdateRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        requireEditable(project);

        projectMapper.updateEntityFromRequest(request, project);

        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(saved);
    }

    /**
     * ACTIVE/ON_HOLD -&gt; COMPLETED. Terminal — no further edits or expenses
     * can be recorded against this project afterward. There is deliberately
     * no separate "cancel" endpoint yet; CANCELLED exists in the status
     * enum for future use but nothing currently transitions a project to it.
     */
    @Transactional
    public ProjectResponse completeProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        requireEditable(project);

        project.setStatus(ProjectStatus.COMPLETED);
        Project saved = projectRepository.save(project);
        return projectMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProjectResponse getById(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        return projectMapper.toResponse(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> search(ProjectStatus status, Pageable pageable) {
        Page<Project> page = projectRepository.search(status, pageable);
        return PageResponse.of(page, projectMapper::toResponse);
    }

    /**
     * Loads a project and confirms it's still ACTIVE/ON_HOLD — shared by
     * update/complete here and by ProjectExpenseService before recording a
     * new expense, so both enforce the identical lock condition.
     */
    @Transactional(readOnly = true)
    public Project requireEditableProject(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        requireEditable(project);
        return project;
    }

    private void requireEditable(Project project) {
        if (project.getStatus() == ProjectStatus.COMPLETED || project.getStatus() == ProjectStatus.CANCELLED) {
            throw new ProjectNotEditableException(project.getId(), project.getStatus());
        }
    }
}
