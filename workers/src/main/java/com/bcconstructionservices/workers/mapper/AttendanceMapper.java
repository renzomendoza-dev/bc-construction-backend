package com.bcconstructionservices.workers.mapper;

import com.bcconstructionservices.projects.service.ProjectLookupHelper;
import com.bcconstructionservices.user.service.UserLookupHelper;
import com.bcconstructionservices.workers.dto.AttendanceCreateRequest;
import com.bcconstructionservices.workers.dto.AttendanceResponse;
import com.bcconstructionservices.workers.entity.Attendance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * toEntity is intentionally thin — worker/rateSnapshot are set by
 * AttendanceService after loading the referenced Worker (needed for the
 * daily-rate snapshot anyway), projectExpenseId is set after the linked
 * ProjectExpense is created, and recordedBy comes from @CreatedBy auditing.
 */
@Mapper(componentModel = "spring", uses = {UserLookupHelper.class, ProjectLookupHelper.class})
public interface AttendanceMapper {

    @Mapping(target = "workerId", source = "worker.id")
    @Mapping(target = "workerName", source = "worker.name")
    @Mapping(target = "projectName", source = "projectId", qualifiedByName = "resolveProjectName")
    @Mapping(target = "amount", expression = "java(attendance.getRateSnapshot().multiply(attendance.getDaysPresent()))")
    @Mapping(target = "recordedByName", source = "recordedBy", qualifiedByName = "resolveUserName")
    AttendanceResponse toResponse(Attendance attendance);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "worker", ignore = true)
    @Mapping(target = "rateSnapshot", ignore = true)
    @Mapping(target = "timeIn", ignore = true) // only ever set via the batch endpoint
    @Mapping(target = "timeOut", ignore = true)
    @Mapping(target = "projectExpenseId", ignore = true)
    @Mapping(target = "recordedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Attendance toEntity(AttendanceCreateRequest request);
}
