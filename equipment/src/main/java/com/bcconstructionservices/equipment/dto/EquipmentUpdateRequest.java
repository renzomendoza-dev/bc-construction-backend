package com.bcconstructionservices.equipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload to update editable equipment details. Status, holder, and site are not editable here — they change only via checkout/checkin.")
public class EquipmentUpdateRequest {

    @NotBlank
    @Size(max = 150)
    @Schema(description = "Equipment name", example = "DeWalt 20V Cordless Drill", maxLength = 150)
    private String name;

    @Size(max = 100)
    @Schema(description = "Equipment category", example = "Power Tools", maxLength = 100)
    private String category;

    @Size(max = 100)
    @Schema(description = "Manufacturer serial number", example = "SN-88213A", maxLength = 100)
    private String serialNumber;

    @DecimalMin(value = "0.0", inclusive = true, message = "purchasePrice cannot be negative")
    @Schema(description = "Purchase price", example = "249.99")
    private BigDecimal purchasePrice;

    @PastOrPresent(message = "purchaseDate cannot be in the future")
    @Schema(description = "Date of purchase", example = "2026-01-15")
    private LocalDate purchaseDate;

    @Size(max = 150)
    @Schema(description = "Vendor equipment was purchased from", example = "Home Depot Pro", maxLength = 150)
    private String purchaseVendor;
}