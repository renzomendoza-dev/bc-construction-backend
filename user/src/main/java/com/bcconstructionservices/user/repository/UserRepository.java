package com.bcconstructionservices.user.repository;

import com.bcconstructionservices.user.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Creates the local profile for a Keycloak subject unless one already
     * exists, atomically. Two concurrent first requests from the same new user
     * can't both insert: the second waits for the first to commit, then does
     * nothing. (A plain save() would fail the second with a unique-constraint
     * violation, and Postgres would abort its transaction, so it couldn't even
     * re-read the winning row.) Returns 1 if a row was inserted, 0 otherwise.
     */
    @Modifying
    @Query(value = """
            INSERT INTO app_user (keycloak_id, full_name, active, created_at, updated_at)
            VALUES (:keycloakId, :fullName, true, now(), now())
            ON CONFLICT (keycloak_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("keycloakId") UUID keycloakId, @Param("fullName") String fullName);

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

    /**
     * Paginated listing filtered by active status, for the admin user list
     * (unfiltered listing uses the inherited {@code findAll(Pageable)}).
     */
    Page<AppUser> findByActive(boolean active, Pageable pageable);
}