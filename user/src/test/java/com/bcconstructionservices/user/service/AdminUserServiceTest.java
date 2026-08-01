package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.dto.AdminUserResponse;
import com.bcconstructionservices.user.dto.PageResponse;
import com.bcconstructionservices.user.dto.RoleResponse;
import com.bcconstructionservices.user.dto.UserResponse;
import com.bcconstructionservices.user.entity.AdminAction;
import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.exception.KeycloakRoleNotFoundException;
import com.bcconstructionservices.user.exception.UserNotFoundException;
import com.bcconstructionservices.user.mapper.UserMapper;
import com.bcconstructionservices.user.repository.UserRepository;
import com.bcconstructionservices.user.service.keycloak.KeycloakAdminClient;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakRoleRepresentation;
import com.bcconstructionservices.user.service.keycloak.dto.KeycloakUserRepresentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private AdminAuditService adminAuditService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AdminUserService adminUserService;

    private UUID adminKeycloakId;
    private Jwt adminJwt;
    private AppUser targetUser;

    @BeforeEach
    void setUp() {
        adminKeycloakId = UUID.randomUUID();
        adminJwt = mock(Jwt.class);
        lenient().when(adminJwt.getSubject()).thenReturn(adminKeycloakId.toString());

        targetUser = AppUser.builder()
                .id(42L)
                .keycloakId(UUID.randomUUID())
                .fullName("Jane Doe")
                .active(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private void stubActorResolution(Long actorId) {
        AppUser admin = AppUser.builder().id(actorId).keycloakId(adminKeycloakId).build();
        when(userRepository.findByKeycloakId(adminKeycloakId)).thenReturn(Optional.of(admin));
    }

    // ---------------------------------------------------------------
    // listUsers()
    // ---------------------------------------------------------------

    @Test
    void listUsers_withActiveFilter_dispatchesToFindByActive() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AppUser> page = new PageImpl<>(List.of(targetUser), pageable, 1);
        when(userRepository.findByActive(true, pageable)).thenReturn(page);
        when(userMapper.toResponse(targetUser)).thenReturn(UserResponse.builder().id(42L).build());

        PageResponse<UserResponse> result = adminUserService.listUsers(true, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listUsers_withoutActiveFilter_dispatchesToFindAll() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AppUser> page = new PageImpl<>(List.of(targetUser), pageable, 1);
        when(userRepository.findAll(pageable)).thenReturn(page);
        when(userMapper.toResponse(targetUser)).thenReturn(UserResponse.builder().id(42L).build());

        PageResponse<UserResponse> result = adminUserService.listUsers(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(userRepository, never()).findByActive(anyBoolean(), any(Pageable.class));
    }

    // ---------------------------------------------------------------
    // getUserDetail()
    // ---------------------------------------------------------------

    @Test
    void getUserDetail_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminUserService.getUserDetail(99L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getUserDetail_found_returnsProfilePlusRealmRoles() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        when(keycloakAdminClient.getUserRealmRoles(targetUser.getKeycloakId()))
                .thenReturn(List.of(new KeycloakRoleRepresentation("r1", "ADMIN", null)));
        when(userMapper.toAdminResponse(targetUser, List.of("ADMIN")))
                .thenReturn(AdminUserResponse.builder().id(42L).realmRoles(List.of("ADMIN")).build());

        AdminUserResponse result = adminUserService.getUserDetail(42L);

        assertThat(result.getRealmRoles()).containsExactly("ADMIN");
    }

    // ---------------------------------------------------------------
    // activateUser() / deactivateUser()
    // ---------------------------------------------------------------

    @Test
    void activateUser_setsActiveTrueSavesAndRecordsAudit() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        stubActorResolution(1L);

        adminUserService.activateUser(42L, adminJwt);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isTrue();
        verify(adminAuditService).record(1L, 42L, AdminAction.ACTIVATE, null);
    }

    @Test
    void deactivateUser_setsActiveFalseSavesAndRecordsAudit() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        stubActorResolution(1L);

        adminUserService.deactivateUser(42L, adminJwt);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        verify(adminAuditService).record(1L, 42L, AdminAction.DEACTIVATE, null);
    }

    // ---------------------------------------------------------------
    // listAvailableRoles()
    // ---------------------------------------------------------------

    @Test
    void listAvailableRoles_mapsClientResultsToRoleResponses() {
        KeycloakRoleRepresentation role = new KeycloakRoleRepresentation("r1", "MANAGER", "desc");
        when(keycloakAdminClient.listRealmRoles()).thenReturn(List.of(role));
        when(userMapper.toRoleResponse(role)).thenReturn(RoleResponse.builder().name("MANAGER").build());

        List<RoleResponse> result = adminUserService.listAvailableRoles();

        assertThat(result).extracting(RoleResponse::getName).containsExactly("MANAGER");
    }

    // ---------------------------------------------------------------
    // assignRole() / revokeRole()
    // ---------------------------------------------------------------

    @Test
    void assignRole_success_recordsAudit() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        stubActorResolution(1L);

        adminUserService.assignRole(42L, "MANAGER", adminJwt);

        verify(keycloakAdminClient).assignRealmRole(targetUser.getKeycloakId(), "MANAGER");
        verify(adminAuditService).record(1L, 42L, AdminAction.ASSIGN_ROLE, "MANAGER");
    }

    @Test
    void assignRole_roleNotFoundOnClient_propagatesAndDoesNotRecordAudit() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        org.mockito.Mockito.doThrow(new KeycloakRoleNotFoundException("BOGUS"))
                .when(keycloakAdminClient).assignRealmRole(targetUser.getKeycloakId(), "BOGUS");

        assertThatThrownBy(() -> adminUserService.assignRole(42L, "BOGUS", adminJwt))
                .isInstanceOf(KeycloakRoleNotFoundException.class);

        verify(adminAuditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void revokeRole_success_recordsAudit() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        stubActorResolution(1L);

        adminUserService.revokeRole(42L, "MANAGER", adminJwt);

        verify(keycloakAdminClient).revokeRealmRole(targetUser.getKeycloakId(), "MANAGER");
        verify(adminAuditService).record(1L, 42L, AdminAction.REVOKE_ROLE, "MANAGER");
    }

    // ---------------------------------------------------------------
    // resyncUser()
    // ---------------------------------------------------------------

    @Test
    void resyncUser_updatesFullNameFromKeycloakProfileAndRecordsAudit() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        stubActorResolution(1L);
        when(keycloakAdminClient.getUserProfile(targetUser.getKeycloakId()))
                .thenReturn(new KeycloakUserRepresentation("kc-id", "jdoe", "Janet", "Doe", true));
        when(keycloakAdminClient.getUserRealmRoles(targetUser.getKeycloakId())).thenReturn(List.of());
        when(userMapper.toAdminResponse(targetUser, List.of()))
                .thenReturn(AdminUserResponse.builder().id(42L).fullName("Janet Doe").build());

        AdminUserResponse result = adminUserService.resyncUser(42L, adminJwt);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("Janet Doe");
        verify(adminAuditService).record(1L, 42L, AdminAction.RESYNC, "Janet Doe");
        assertThat(result.getFullName()).isEqualTo("Janet Doe");
    }

    @Test
    void resyncUser_blankFirstAndLastName_fallsBackToUsername() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(targetUser));
        stubActorResolution(1L);
        when(keycloakAdminClient.getUserProfile(targetUser.getKeycloakId()))
                .thenReturn(new KeycloakUserRepresentation("kc-id", "jdoe", null, null, true));
        when(keycloakAdminClient.getUserRealmRoles(targetUser.getKeycloakId())).thenReturn(List.of());
        when(userMapper.toAdminResponse(any(), any()))
                .thenReturn(AdminUserResponse.builder().id(42L).fullName("jdoe").build());

        adminUserService.resyncUser(42L, adminJwt);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFullName()).isEqualTo("jdoe");
    }
}
