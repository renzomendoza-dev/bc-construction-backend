package com.bcconstructionservices.projects.service;

import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.projects.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

/**
 * Lets other modules (e.g. workers) resolve a display name for a project id
 * they hold as a plain Long — mirrors
 * {@code com.bcconstructionservices.user.service.UserLookupHelper}'s role
 * for cross-module user-id lookups inside a MapStruct mapper.
 */
@Component
@RequiredArgsConstructor
public class ProjectLookupHelper {

    private final ProjectRepository projectRepository;

    @Named("resolveProjectName")
    public String resolveProjectName(Long projectId) {
        if (projectId == null) {
            return null;
        }
        return projectRepository.findById(projectId)
                .map(Project::getName)
                .orElse(null);
    }
}
