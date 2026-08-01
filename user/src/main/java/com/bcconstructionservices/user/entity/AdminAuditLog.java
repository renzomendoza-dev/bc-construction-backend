package com.bcconstructionservices.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Immutable audit trail row for an admin action taken against a user, e.g.
 * activating/deactivating an account or assigning/revoking a Keycloak realm
 * role. Rows are only ever inserted, never updated.
 * <p>
 * {@link #actorUserId} and {@link #targetUserId} are plain {@code Long}
 * columns with no foreign key to {@code app_user}, matching this codebase's
 * existing convention for person-reference columns (see equipment's
 * {@code current_holder_id}/{@code created_by}/{@code updated_by}) — this
 * keeps the audit trail insertable even if a referenced row is ever
 * hard-deleted.
 */
@Entity
@Table(name = "admin_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Local {@code AppUser} id of the admin who performed the action. Nullable
     * for the (currently theoretical) case of a system-triggered action.
     */
    @Column(name = "actor_user_id")
    private Long actorUserId;

    /**
     * Local {@code AppUser} id of the user the action was performed against.
     */
    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private AdminAction action;

    /**
     * Optional extra context, e.g. the role name for ASSIGN_ROLE/REVOKE_ROLE.
     */
    @Column(name = "detail", length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
