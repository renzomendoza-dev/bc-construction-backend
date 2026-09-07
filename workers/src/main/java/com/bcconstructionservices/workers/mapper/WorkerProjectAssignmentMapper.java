package com.bcconstructionservices.workers.mapper;

import com.bcconstructionservices.projects.service.ProjectLookupHelper;
import com.bcconstructionservices.user.service.UserLookupHelper;
import com.bcconstructionservices.workers.dto.WorkerProjectAssignmentResponse;
import com.bcconstructionservices.workers.entity.WorkerProjectAssignment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * No toEntity — WorkerProjectAssignmentService builds the entity directly
 * after loading the referenced Worker (needed for the duplicate-active-assignment
 * check anyway), same reasoning as TransferLineItemMapper's own javadoc.
 */
@Mapper(componentModel = "spring", uses = {UserLookupHelper.class, ProjectLookupHelper.class})
public interface WorkerProjectAssignmentMapper {

    @Mapping(target = "workerId", source = "worker.id")
    @Mapping(target = "workerName", source = "worker.name")
    @Mapping(target = "projectName", source = "projectId", qualifiedByName = "resolveProjectName")
    @Mapping(target = "createdByName", source = "createdBy", qualifiedByName = "resolveUserName")
    WorkerProjectAssignmentResponse toResponse(WorkerProjectAssignment assignment);
}
