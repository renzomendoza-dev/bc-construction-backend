package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

/**
 * syncFromToken's contract. Writes to an existing, managed AppUser happen via
 * Hibernate dirty checking at commit, so they're asserted on the returned
 * entity rather than via save(). The real concurrent-insert behavior against
 * Postgres is covered by app's UserSyncConcurrencyTest.
 */
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

    private AppUser storedUser(String fullName) {
        return AppUser.builder()
                .id(1L)
                .keycloakId(keycloakId)
                .fullName(fullName)
                .active(true)
                .createdAt(Instant.now().minusSeconds(3600))
                .updatedAt(Instant.now().minusSeconds(3600))
                .build();
    }

    @Test
    void syncFromToken_existingUserWithChangedName_refreshesFullNameWithoutInserting() {
        when(jwt.getClaimAsString("name")).thenReturn("Jane Doe Updated");
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(storedUser("Jane Doe Old")));

        AppUser result = userSyncService.syncFromToken(jwt);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getFullName()).isEqualTo("Jane Doe Updated");
        verify(userRepository, never()).insertIfAbsent(any(), any());
    }

    @Test
    void syncFromToken_existingUserWithSameName_changesNothing() {
        AppUser existing = storedUser("Jane Doe");
        Instant updatedAt = existing.getUpdatedAt();
        when(jwt.getClaimAsString("name")).thenReturn("Jane Doe");
        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(existing));

        AppUser result = userSyncService.syncFromToken(jwt);

        assertThat(result).isSameAs(existing);
        assertThat(result.getUpdatedAt()).isEqualTo(updatedAt);
        verify(userRepository).findByKeycloakId(keycloakId);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void syncFromToken_noExistingUser_insertsAtomicallyAndReturnsTheStoredRow() {
        when(jwt.getClaimAsString("name")).thenReturn("New User");
        AppUser created = storedUser("New User");
        when(userRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));
        when(userRepository.insertIfAbsent(keycloakId, "New User")).thenReturn(1);

        AppUser result = userSyncService.syncFromToken(jwt);

        verify(userRepository).insertIfAbsent(keycloakId, "New User");
        verify(userRepository, never()).save(any());
        assertThat(result).isSameAs(created);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void syncFromToken_nameClaimMissing_fallsBackToPreferredUsername() {
        when(jwt.getClaimAsString("name")).thenReturn(null);
        when(jwt.getClaimAsString("preferred_username")).thenReturn("jdoe");
        when(userRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(storedUser("jdoe")));
        when(userRepository.insertIfAbsent(keycloakId, "jdoe")).thenReturn(1);

        AppUser result = userSyncService.syncFromToken(jwt);

        verify(userRepository).insertIfAbsent(keycloakId, "jdoe");
        assertThat(result.getFullName()).isEqualTo("jdoe");
    }

    @Test
    void syncFromToken_losingAConcurrentFirstInsert_returnsTheWinnersRow() {
        // Another request for the same new user inserted between our lookup
        // and our insert, so ON CONFLICT made ours a no-op (0 rows).
        when(jwt.getClaimAsString("name")).thenReturn("New User");
        AppUser winners = storedUser("New User");
        when(userRepository.findByKeycloakId(keycloakId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winners));
        when(userRepository.insertIfAbsent(keycloakId, "New User")).thenReturn(0);

        AppUser result = userSyncService.syncFromToken(jwt);

        assertThat(result).isSameAs(winners);
    }
}
