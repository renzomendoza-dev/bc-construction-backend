package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Keeps the local {@link AppUser} profile in sync with the Keycloak identity
 * carried in an authenticated request's JWT.
 * <p>
 * Keycloak is the sole owner of identity, authentication, and authorization
 * data (credentials, email, roles). This service does not manage any of
 * that — it only maintains a minimal local row (keyed by the Keycloak
 * subject) so that other modules can attach foreign keys (e.g.
 * {@code created_by}) to a local user without calling Keycloak's admin API
 * on every request.
 * <p>
 * {@link #syncFromToken(Jwt)} is intended to run on <strong>every</strong>
 * authenticated request — typically invoked from a security filter or
 * interceptor after the JWT has been validated — not just on first login.
 * This ensures the locally cached {@code fullName} stays fresh with
 * whatever the user's current Keycloak profile reports.
 */
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final UserRepository userRepository;

    /**
     * Syncs the local {@link AppUser} profile from the given validated JWT.
     * <p>
     * Extracts the Keycloak subject ("sub" claim) and display name ("name"
     * claim, falling back to "preferred_username") from the token. If a
     * local profile already exists for that subject, its {@code fullName}
     * is refreshed. Otherwise, a new active local profile is created.
     *
     * @param jwt the validated Keycloak-issued JWT for the current request
     * @return the synced (existing or newly created) {@link AppUser}
     */
    @Transactional
    public AppUser syncFromToken(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());
        String fullName = resolveFullName(jwt);

        return userRepository.findByKeycloakId(keycloakId)
                .map(existingUser -> {
                    existingUser.setFullName(fullName);
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> {
                    AppUser newUser = AppUser.builder()
                            .keycloakId(keycloakId)
                            .fullName(fullName)
                            .active(true)
                            .build();
                    return userRepository.save(newUser);
                });
    }

    private String resolveFullName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return jwt.getClaimAsString("preferred_username");
    }
}