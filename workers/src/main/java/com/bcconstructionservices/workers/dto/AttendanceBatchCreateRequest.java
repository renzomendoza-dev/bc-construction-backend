package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to record attendance for multiple workers on one project/day in a single "
        + "batch. Workers who already have a record for this date are skipped (reported in the response, not an "
        + "error) — a genuine failure on any other entry (e.g. an inactive worker, or the project locked) aborts "
        + "the whole batch.")
public class AttendanceBatchCreateRequest {

    @NotNull
    @Schema(description = "Identifier of the project this labor is attributed to", example = "12")
    private Long projectId;

    @NotNull
    @Schema(description = "Date this attendance covers, for every entry", example = "2026-09-07")
    private LocalDate date;

    @NotEmpty(message = "A batch must have at least one entry")
    @Valid
    @Schema(description = "One entry per worker; a given workerId must not appear more than once (400 otherwise)")
    private List<AttendanceBatchLineRequest> entries;
}
