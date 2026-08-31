package com.bcconstructionservices.equipment.dto;

import com.bcconstructionservices.equipment.entity.EquipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Equipment details, including resolved current holder name")
public class EquipmentResponse {

    @Schema(example = "42")
    private Long id;

    @Schema(example = "EQ-2026-0042")
    private String assetTag;

    @Schema(example = "DeWalt 20V Cordless Drill")
    private String name;

    @Schema(example = "Power Tools")
    private String category;

    @Schema(example = "SN-88213A")
    private String serialNumber;

    @Schema(example = "CHECKED_OUT")
    private EquipmentStatus status;

    @Schema(description = "App-local user ID currently holding this equipment, if checked out", example = "17")
    private Long currentHolderId;

    @Schema(description = "Resolved display name of the current holder, if checked out", example = "Maria Santos")
    private String currentHolderName;

    @Schema(example = "Site B - Riverside")
    private String currentSite;

    private Instant checkedOutAt;

    @Schema(example = "249.99")
    private BigDecimal purchasePrice;

    private LocalDate purchaseDate;

    @Schema(example = "Home Depot Pro")
    private String purchaseVendor;

    private Long createdBy;
    private Long updatedBy;
    private Instant createdAt;
    private Instant updatedAt;
}