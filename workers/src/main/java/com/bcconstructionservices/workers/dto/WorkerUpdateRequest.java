package com.bcconstructionservices.workers.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to update a worker's editable fields")
public class WorkerUpdateRequest {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Worker's full name", example = "Ramon Villanueva")
    private String name;

    @Size(max = 100)
    @Schema(description = "Optional role/position", example = "Mason")
    private String position;

    @NotNull
    @Positive
    @Schema(description = "Daily rate in PHP", example = "800.00")
    private BigDecimal dailyRate;
}
