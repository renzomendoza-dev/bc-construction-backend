package com.bcconstructionservices.equipment.dto;

import com.bcconstructionservices.equipment.entity.EquipmentAssignmentBatchStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A batch of equipment assignments (checkouts) or returns (check-ins), along with its lines")
public class EquipmentAssignmentBatchResponse {

    @Schema(example = "15")
    private Long id;

    @Schema(example = "DRAFT", allowableValues = {"DRAFT", "SUBMITTED", "COMPLETED"})
    private EquipmentAssignmentBatchStatus status;

    @Schema(description = "Identifier of the destination warehouse", example = "2")
    private Long destinationWarehouseId;

    @Schema(description = "Resolved display name of the destination warehouse", example = "Site B - Riverside")
    private String destinationWarehouseName;

    @Schema(description = "App-local user id taking custody, for an assign-out batch; null for a return batch",
            example = "17")
    private Long holderId;

    @Schema(description = "Resolved display name of the holder, if any", example = "Maria Santos")
    private String holderName;

    @Schema(description = "ID of the user who created this batch", example = "3")
    private Long initiatedBy;

    @Schema(description = "Full name of the user who created this batch", example = "Juan Dela Cruz")
    private String initiatedByName;

    @Schema(example = "Weekly dispatch for Sta. Maria site")
    private String notes;

    @Schema(description = "Equipment included in this batch")
    private List<EquipmentAssignmentBatchLineResponse> lines;

    private Instant createdAt;
    private Instant updatedAt;
}
