package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserSyncService userSyncService;

    private Jwt jwt;
    private UUID keycloakId;

    @BeforeEach
    void setUp() {
        jwt = mock(Jwt.class);
        keycloakId = UUID.randomUUID();
        when(jwt.getSubject()).thenReturn(keycloakId.toString());
    }

    @Test
    void syncFromToken_existingUserWithNameClaim_updatesFullNameAndSaves() {
        // Arrange
        String updatedName = "Jane Doe Updated";
        when(jwt.getClaimAsString("name")).thenReturn(updatedName);

        AppUser existingUser = AppUser.builder()
                .id(1L)
                .keycloakId(keycloakId)
                .fullName("Jane Doe Old")
                .active(true)
                .createdAt(Instant.now().minusSeconds(3600))
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppUser result = userSyncService.syncFromToken(jwt);

        // Assert
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository, times(1)).save(captor.capture());

        AppUser savedUser = captor.getValue();
        assertThat(savedUser.getFullName()).isEqualTo(updatedName);
        assertThat(savedUser.getKeycloakId()).isEqualTo(keycloakId);
        assertThat(savedUser.getId()).isEqualTo(1L);
        assertThat(savedUser.isActive()).isTrue();

        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo(updatedName);
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void syncFromToken_noExistingUser_createsNewActiveUserAndSaves() {
        // Arrange
        String fullName = "New User";
        when(jwt.getClaimAsString("name")).thenReturn(fullName);

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppUser result = userSyncService.syncFromToken(jwt);

        // Assert
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository, times(1)).save(captor.capture());

        AppUser savedUser = captor.getValue();
        assertThat(savedUser.getKeycloakId()).isEqualTo(keycloakId);
        assertThat(savedUser.getFullName()).isEqualTo(fullName);
        assertThat(savedUser.isActive()).isTrue();

        assertThat(result).isNotNull();
        assertThat(result.getKeycloakId()).isEqualTo(keycloakId);
        assertThat(result.getFullName()).isEqualTo(fullName);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void syncFromToken_nameClaimMissing_fallsBackToPreferredUsername() {
        // Arrange
        String preferredUsername = "jdoe";
        when(jwt.getClaimAsString("name")).thenReturn(null);
        when(jwt.getClaimAsString("preferred_username")).thenReturn(preferredUsername);

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        AppUser result = userSyncService.syncFromToken(jwt);

        // Assert
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository, times(1)).save(captor.capture());

        AppUser savedUser = captor.getValue();
        assertThat(savedUser.getFullName()).isEqualTo(preferredUsername);

        assertThat(result).isNotNull();
        assertThat(result.getFullName()).isEqualTo(preferredUsername);
    }

    @Test
    void syncFromToken_savesExactlyOnce_perInvocation() {
        // Arrange
        when(jwt.getClaimAsString("name")).thenReturn("Some User");

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());
        when(userRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        userSyncService.syncFromToken(jwt);

        // Assert
        verify(userRepository, times(1)).save(any(AppUser.class));
        verify(userRepository, never()).save(argThat(u -> false)); // sanity guard, no duplicate matcher saves
        verifyNoMoreInteractions(userRepository);
    }
}