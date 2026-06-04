-- Auth service Flyway migration
-- References the canonical SQL in StoreSense/sql/01_auth.sql
-- Runs within the 'auth' schema only

SET search_path TO auth;

CREATE TABLE IF NOT EXISTS roles (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_roles      PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS users (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    username                VARCHAR(100) NOT NULL,
    email                   VARCHAR(255) NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    store_id                UUID,
    is_active               BOOLEAN      NOT NULL DEFAULT true,
    last_login_at           TIMESTAMPTZ,
    password_changed_at     TIMESTAMPTZ,
    failed_login_attempts   INT          NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              UUID,
    CONSTRAINT pk_users           PRIMARY KEY (id),
    CONSTRAINT uq_users_username  UNIQUE (username),
    CONSTRAINT uq_users_email     UNIQUE (email),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id    UUID        NOT NULL,
    role_id    UUID        NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID,
    CONSTRAINT pk_user_roles         PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user    FOREIGN KEY (user_id)    REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role    FOREIGN KEY (role_id)    REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_granter FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    token_hash         VARCHAR(255) NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    issued_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at         TIMESTAMPTZ,
    device_fingerprint VARCHAR(255),
    ip_address         TEXT,
    CONSTRAINT pk_refresh_tokens      PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_users_store_id
    ON users (store_id) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active
    ON refresh_tokens (user_id, expires_at) WHERE revoked_at IS NULL;
