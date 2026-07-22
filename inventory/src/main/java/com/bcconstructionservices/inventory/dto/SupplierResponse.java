package com.bcconstructionservices.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response payload representing a Supplier.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierResponse {

    @Schema(description = "Unique identifier of the supplier", example = "1")
    private Long id;

    @Schema(description = "Name of the supplier", example = "Acme Distribution Co.")
    private String name;

    @Schema(description = "Contact details for the supplier, e.g. phone, email, or address", example = "jane.doe@acmedist.com / +1-555-0142")
    private String contactInfo;

    @Schema(description = "Whether the supplier is currently active", example = "true")
    private boolean active;

    @Schema(description = "Timestamp when the supplier was created", example = "2026-07-18T09:15:30Z")
    private Instant createdAt;

    @Schema(description = "Timestamp when the supplier was last updated", example = "2026-07-18T09:15:30Z")
    private Instant updatedAt;
}
