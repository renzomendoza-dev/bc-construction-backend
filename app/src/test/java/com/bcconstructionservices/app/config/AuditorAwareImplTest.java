package com.bcconstructionservices.app.config;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Note on approach: rather than statically mocking SecurityContextHolder via MockedStatic,
 * this test uses the standard/idiomatic approach of setting a real SecurityContext
 * (populated with a mocked Authentication) directly on SecurityContextHolder via
 * SecurityContextHolder.setContext(...). This avoids the overhead/fragility of static
 * mocking while still exercising the real SecurityContextHolder.getContext().getAuthentication()
 * call path used by AuditorAwareImpl. The context is cleared after each test to prevent
 * state leaking between tests.
 */
@ExtendWith(MockitoExtension.class)
class AuditorAwareImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuditorAwareImpl auditorAwareImpl;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentAuditor_noAuthenticationPresent_returnsEmpty() {
        // Arrange
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        SecurityContextHolder.setContext(securityContext);

        // Act
        Optional<Long> result = auditorAwareImpl.getCurrentAuditor();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void getCurrentAuditor_principalNotJwt_returnsEmpty() {
        // Arrange
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "some-string-principal", null);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Act
        Optional<Long> result = auditorAwareImpl.getCurrentAuditor();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void getCurrentAuditor_validJwtPrincipalWithMatchingAppUser_returnsAppUserId() {
        // Arrange
        UUID keycloakId = UUID.randomUUID();
        Long expectedUserId = 42L;

        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", keycloakId.toString())
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(jwt, null);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        AppUser appUser = AppUser.builder()
                .id(expectedUserId)
                .keycloakId(keycloakId)
                .build();

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.of(appUser));

        // Act
        Optional<Long> result = auditorAwareImpl.getCurrentAuditor();

        // Assert
        assertThat(result).isPresent().contains(expectedUserId);
    }

    @Test
    void getCurrentAuditor_validJwtPrincipalWithNoMatchingAppUser_returnsEmpty() {
        // Arrange
        UUID keycloakId = UUID.randomUUID();

        Jwt jwt = Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("sub", keycloakId.toString())
                .build();

        Authentication authentication = new UsernamePasswordAuthenticationToken(jwt, null);

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findByKeycloakId(keycloakId)).thenReturn(Optional.empty());

        // Act
        Optional<Long> result = auditorAwareImpl.getCurrentAuditor();

        // Assert
        assertThat(result).isEmpty();
    }
}