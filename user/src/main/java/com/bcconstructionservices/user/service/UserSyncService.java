package com.bcconstructionservices.user.service;

import com.bcconstructionservices.user.entity.AppUser;
import com.bcconstructionservices.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
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
 * {@link #syncFromToken(Jwt)} runs on <strong>every</strong> authenticated
 * {@code /api/**} request, from the app module's {@code UserSyncInterceptor}
 * (after the JWT has been validated, before the controller), and again from
 * {@code GET /api/users/me}. So the local row always exists before any
 * request's own code needs it (e.g. CurrentUserService, created_by
 * auditing), and {@code fullName} stays fresh with the user's current
 * Keycloak profile.
 */
@Service
@RequiredArgsConstructor
public class UserSyncService {

    private final UserRepository userRepository;

    /**
     * Syncs the local {@link AppUser} profile from the given validated JWT.
     * <p>
     * Extracts the Keycloak subject ("sub" claim) and display name ("name"
     * claim, falling back to "preferred_username") from the token. If no
     * local profile exists for that subject, one is created — atomically, so
     * concurrent first requests from a new user can't collide (see
     * {@link UserRepository#insertIfAbsent}). Its {@code fullName} is then
     * refreshed if the token reports a different one; an unchanged profile
     * is never written.
     *
     * @param jwt the validated Keycloak-issued JWT for the current request
     * @return the synced (existing or newly created) {@link AppUser}
     */
    @Transactional
    public AppUser syncFromToken(Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());
        String fullName = resolveFullName(jwt);

        AppUser user = userRepository.findByKeycloakId(keycloakId).orElse(null);
        if (user == null) {
            userRepository.insertIfAbsent(keycloakId, fullName);
            user = userRepository.findByKeycloakId(keycloakId).orElseThrow();
        }
        if (!Objects.equals(user.getFullName(), fullName)) {
            user.setFullName(fullName);
        }
        return user;
    }

    private String resolveFullName(Jwt jwt) {
        String name = jwt.getClaimAsString("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        return jwt.getClaimAsString("preferred_username");
    }
}