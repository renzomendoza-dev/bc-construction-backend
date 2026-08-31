-- V13: admin_audit_log
-- Deliberately FK-free on actor_user_id/target_user_id: an audit trail must
-- survive even if the app_user row it refers to is later hard-deleted.

CREATE TABLE admin_audit_log (
    id             BIGSERIAL PRIMARY KEY,
    actor_user_id  BIGINT,
    target_user_id BIGINT NOT NULL,
    action         VARCHAR(30) NOT NULL
        CONSTRAINT admin_audit_log_action_check
            CHECK (action IN ('ACTIVATE','DEACTIVATE','ASSIGN_ROLE','REVOKE_ROLE','RESYNC')),
    detail         VARCHAR(500),
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_log_target_user ON admin_audit_log(target_user_id);
CREATE INDEX idx_admin_audit_log_actor_user ON admin_audit_log(actor_user_id);
