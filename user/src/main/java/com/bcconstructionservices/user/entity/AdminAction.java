package com.bcconstructionservices.user.entity;

/**
 * The kind of admin action recorded in {@link AdminAuditLog}.
 */
public enum AdminAction {
    ACTIVATE,
    DEACTIVATE,
    ASSIGN_ROLE,
    REVOKE_ROLE,
    RESYNC
}
