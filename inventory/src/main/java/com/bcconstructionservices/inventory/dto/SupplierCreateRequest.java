package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request payload for creating a new Supplier.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierCreateRequest {

    @NotBlank
    @Schema(description = "Name of the supplier", example = "Acme Distribution Co.")
    private String name;

    @Schema(description = "Contact details for the supplier, e.g. phone, email, or address", example = "jane.doe@acmedist.com / +1-555-0142")
    private String contactInfo;
}
