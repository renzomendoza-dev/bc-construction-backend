package com.bcconstructionservices.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleAssignmentRequest {

    @Schema(description = "Name of the Keycloak realm role to assign.", example = "MANAGER")
    @NotBlank
    private String roleName;
}
