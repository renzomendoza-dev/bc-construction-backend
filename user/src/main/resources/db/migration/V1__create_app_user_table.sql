-- V1: app_user
-- Local, app-specific profile for a user, linked to a Keycloak identity via
-- keycloak_id (the JWT "sub" claim). Identity, authentication, and
-- authorization data (credentials, email, roles) are owned exclusively by
-- Keycloak and are not stored here. Created first since most other tables'
-- created_by/updated_by/actor columns reference it.

CREATE TABLE app_user (
    id           BIGSERIAL                   PRIMARY KEY,
    keycloak_id  UUID                         NOT NULL,
    full_name    VARCHAR(255)                 NOT NULL,
    active       BOOLEAN                      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    updated_at   TIMESTAMP(6) WITH TIME ZONE  NOT NULL,
    CONSTRAINT uk_app_user_keycloak_id UNIQUE (keycloak_id)
);
