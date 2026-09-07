package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * One worker's entry within an AttendanceBatchCreateRequest. No daysPresent —
 * it's derived from timeIn/timeOut (capped at one standard 8-hour day; see
 * AttendanceService#deriveDaysPresent).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceBatchLineRequest {

    @NotNull
    @Schema(description = "Identifier of the worker who was present", example = "1")
    private Long workerId;

    @NotNull
    @Schema(description = "Clock-in time", example = "07:00:00")
    private LocalTime timeIn;

    @NotNull
    @Schema(description = "Clock-out time; must be after timeIn (400 otherwise)", example = "16:00:00")
    private LocalTime timeOut;

    @Size(max = 1000)
    @Schema(description = "Optional notes", example = "Rebar tying, Building A")
    private String notes;
}
