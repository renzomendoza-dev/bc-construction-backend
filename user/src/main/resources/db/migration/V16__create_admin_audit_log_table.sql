CREATE TABLE admin_audit_log (
    id             BIGSERIAL PRIMARY KEY,
    actor_user_id  BIGINT,
    target_user_id BIGINT NOT NULL,
    action         VARCHAR(30) NOT NULL
        CONSTRAINT admin_audit_log_action_check
            CHECK (action IN ('ACTIVATE','DEACTIVATE','ASSIGN_ROLE','REVOKE_ROLE','RESYNC')),
    detail         VARCHAR(500),
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_log_target_user ON admin_audit_log(target_user_id);
CREATE INDEX idx_admin_audit_log_actor_user ON admin_audit_log(actor_user_id);
