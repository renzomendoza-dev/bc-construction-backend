package com.bcconstructionservices.user.repository;

import com.bcconstructionservices.user.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AppUser}.
 * <p>
 * Provides lookup and existence-check operations keyed by the Keycloak
 * subject ("sub" claim) so that {@code UserSyncService} can find or create a
 * user's local profile on every request/login, as well as a query for
 * listing active users for future admin/assignment use cases.
 */
@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Primary lookup used by UserSyncService to find a user's local profile
     * by their Keycloak subject ("sub" claim).
     */
    Optional<AppUser> findByKeycloakId(UUID keycloakId);

    /**
     * Cheap existence check to distinguish "insert" vs "update" during sync
     * without loading the full entity.
     */
    boolean existsByKeycloakId(UUID keycloakId);

    /**
     * Returns all active users, e.g. for an admin picker or assignment
     * dropdown.
     */
    List<AppUser> findByActiveTrue();
}