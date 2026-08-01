package com.bcconstructionservices.user.mapper;

import com.bcconstructionservices.user.dto.AdminUserResponse;
import com.bcconstructionservices.user.dto.RoleResponse;
import com.bcconstructionservices.user.dto.UserResponse;
import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakRoleRepresentation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * MapStruct mapper for converting {@link AppUser} entities to {@link UserResponse} DTOs.
 * <p>
 * All fields map 1:1 by name since {@code AppUser} and {@code UserResponse} share
 * identical field names and types. No {@code toEntity()} method is provided, since
 * {@code AppUser} is never constructed from a request DTO — it is only ever built
 * internally by {@code UserSyncService} from Keycloak JWT claims.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(AppUser appUser);

    @Mapping(target = "realmRoles", source = "realmRoles")
    AdminUserResponse toAdminResponse(AppUser appUser, List<String> realmRoles);

    RoleResponse toRoleResponse(KeycloakRoleRepresentation role);
}