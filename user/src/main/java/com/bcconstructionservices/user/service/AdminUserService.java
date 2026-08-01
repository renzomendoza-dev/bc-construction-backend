package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.dto.AdminUserResponse;
import com.bcconstructionservices.user.dto.PageResponse;
import com.bcconstructionservices.user.dto.RoleResponse;
import com.bcconstructionservices.user.dto.UserResponse;
import com.bcconstructionservices.user.entity.AdminAction;
import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.exception.UserNotFoundException;
import com.bcconstructionservices.user.mapper.UserMapper;
import com.bcconstructionservices.user.repository.UserRepository;
import com.bcconstructionservices.user.service.keycloak.KeycloakAdminClient;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakRoleRepresentation;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakUserRepresentation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates admin operations on users: listing/detail, activation,
 * Keycloak realm role assignment/revocation, and manual re-sync from
 * Keycloak. Every mutating action is recorded via {@link AdminAuditService}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;
    private final AdminAuditService adminAuditService;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(Boolean active, Pageable pageable) {
        Page<AppUser> page = active != null
                ? userRepository.findByActive(active, pageable)
                : userRepository.findAll(pageable);
        return PageResponse.of(page, userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserDetail(Long userId) {
        AppUser appUser = findUser(userId);
        return userMapper.toAdminResponse(appUser, fetchRoleNames(appUser));
    }

    public void activateUser(Long userId, Jwt adminJwt) {
        AppUser appUser = findUser(userId);
        appUser.setActive(true);
        userRepository.save(appUser);
        adminAuditService.record(resolveActorId(adminJwt), userId, AdminAction.ACTIVATE, null);
    }

    public void deactivateUser(Long userId, Jwt adminJwt) {
        AppUser appUser = findUser(userId);
        appUser.setActive(false);
        userRepository.save(appUser);
        adminAuditService.record(resolveActorId(adminJwt), userId, AdminAction.DEACTIVATE, null);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listAvailableRoles() {
        return keycloakAdminClient.listRealmRoles().stream()
                .map(userMapper::toRoleResponse)
                .toList();
    }

    public void assignRole(Long userId, String roleName, Jwt adminJwt) {
        AppUser appUser = findUser(userId);
        keycloakAdminClient.assignRealmRole(appUser.getKeycloakId(), roleName);
        adminAuditService.record(resolveActorId(adminJwt), userId, AdminAction.ASSIGN_ROLE, roleName);
    }

    public void revokeRole(Long userId, String roleName, Jwt adminJwt) {
        AppUser appUser = findUser(userId);
        keycloakAdminClient.revokeRealmRole(appUser.getKeycloakId(), roleName);
        adminAuditService.record(resolveActorId(adminJwt), userId, AdminAction.REVOKE_ROLE, roleName);
    }

    public AdminUserResponse resyncUser(Long userId, Jwt adminJwt) {
        AppUser appUser = findUser(userId);
        KeycloakUserRepresentation profile = keycloakAdminClient.getUserProfile(appUser.getKeycloakId());
        String fullName = resolveFullName(profile);
        appUser.setFullName(fullName);
        userRepository.save(appUser);
        adminAuditService.record(resolveActorId(adminJwt), userId, AdminAction.RESYNC, fullName);
        return userMapper.toAdminResponse(appUser, fetchRoleNames(appUser));
    }

    private AppUser findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    private List<String> fetchRoleNames(AppUser appUser) {
        return keycloakAdminClient.getUserRealmRoles(appUser.getKeycloakId()).stream()
                .map(KeycloakRoleRepresentation::name)
                .toList();
    }

    /**
     * Resolves the acting admin's local {@code AppUser} id from their JWT,
     * the same way {@code AuditorAwareImpl} resolves an auditor — but passed
     * explicitly through the call chain rather than read from
     * {@code SecurityContextHolder}, for easier unit testing.
     */
    private Long resolveActorId(Jwt adminJwt) {
        UUID keycloakId = UUID.fromString(adminJwt.getSubject());
        return userRepository.findByKeycloakId(keycloakId).map(AppUser::getId).orElse(null);
    }

    private String resolveFullName(KeycloakUserRepresentation profile) {
        String firstName = profile.firstName();
        String lastName = profile.lastName();
        String combined = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
        if (!combined.isBlank()) {
            return combined;
        }
        return profile.username();
    }
}
