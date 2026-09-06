package com.bcconstructionservices.projects.mapper;

import com.bcconstructionservices.projects.dto.ProjectCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectResponse;
import com.bcconstructionservices.projects.dto.ProjectUpdateRequest;
import com.bcconstructionservices.projects.entity.Project;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * toEntity/updateEntityFromRequest are intentionally thin — status is
 * system-managed (left at the entity's own DRAFT-equivalent default on
 * create, changed only via ProjectService.completeProject), initiatedBy
 * comes from @CreatedBy auditing, and code is immutable after creation.
 */
@Mapper(componentModel = "spring", uses = UserLookupHelper.class)
public interface ProjectMapper {

    @Mapping(target = "initiatedByName", source = "initiatedBy", qualifiedByName = "resolveUserName")
    ProjectResponse toResponse(Project project);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true) // left at the entity's own field default (ACTIVE)
    @Mapping(target = "initiatedBy", ignore = true) // set by @CreatedBy auditing
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Project toEntity(ProjectCreateRequest request);

    // Full-replacement PUT semantics — no NullValuePropertyMappingStrategy.IGNORE,
    // so an explicit null in the request clears description/budget/endDate
    // rather than leaving them as-is.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true) // immutable after creation
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "initiatedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(ProjectUpdateRequest request, @MappingTarget Project project);
}
