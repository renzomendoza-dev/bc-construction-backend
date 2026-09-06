package com.bcconstructionservices.workers.mapper;

import com.bcconstructionservices.workers.dto.WorkerCreateRequest;
import com.bcconstructionservices.workers.dto.WorkerResponse;
import com.bcconstructionservices.workers.dto.WorkerUpdateRequest;
import com.bcconstructionservices.workers.entity.Worker;
import com.bcconstructionservices.user.service.UserLookupHelper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * toEntity/updateEntityFromRequest are intentionally thin — active stays at
 * the entity's own default (true) on create and is only ever changed via
 * WorkerService.deactivateWorker, and createdBy comes from @CreatedBy auditing.
 */
@Mapper(componentModel = "spring", uses = UserLookupHelper.class)
public interface WorkerMapper {

    @Mapping(target = "createdByName", source = "createdBy", qualifiedByName = "resolveUserName")
    WorkerResponse toResponse(Worker worker);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true) // left at the entity's own field default (true)
    @Mapping(target = "createdBy", ignore = true) // set by @CreatedBy auditing
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Worker toEntity(WorkerCreateRequest request);

    // Full-replacement PUT semantics — no NullValuePropertyMappingStrategy.IGNORE,
    // matching this codebase's established convention for update requests.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(WorkerUpdateRequest request, @MappingTarget Worker worker);
}
