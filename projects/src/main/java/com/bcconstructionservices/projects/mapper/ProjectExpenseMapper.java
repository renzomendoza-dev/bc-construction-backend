package com.bcconstructionservices.projects.mapper;

import com.bcconstructionservices.projects.dto.ProjectExpenseCreateRequest;
import com.bcconstructionservices.projects.dto.ProjectExpenseResponse;
import com.bcconstructionservices.projects.entity.ProjectExpense;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * toEntity is intentionally thin — project comes from a repository lookup
 * (and gets its editable-status validated there), and recordedBy is set by
 * @CreatedBy auditing; neither is mapper territory.
 */
@Mapper(componentModel = "spring", uses = UserLookupHelper.class)
public interface ProjectExpenseMapper {

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "recordedByName", source = "recordedBy", qualifiedByName = "resolveUserName")
    ProjectExpenseResponse toResponse(ProjectExpense expense);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "recordedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    ProjectExpense toEntity(ProjectExpenseCreateRequest request);
}
