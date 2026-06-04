-- =============================================================================
-- StoreLense: Schema auth
-- Owner: auth-service
-- =============================================================================

-- ----------------------------------------------------------------------------
-- auth.roles
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.roles (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    name        VARCHAR(50) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

-- ----------------------------------------------------------------------------
-- auth.users
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.users (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    username                VARCHAR(100) NOT NULL,
    email                   VARCHAR(255) NOT NULL,
    password_hash           VARCHAR(255) NOT NULL,
    first_name              VARCHAR(100) NOT NULL,
    last_name               VARCHAR(100) NOT NULL,
    store_id                UUID,                          -- NULL = ADMIN (all stores)
    is_active               BOOLEAN      NOT NULL DEFAULT true,
    last_login_at           TIMESTAMPTZ,
    password_changed_at     TIMESTAMPTZ,
    failed_login_attempts   INT          NOT NULL DEFAULT 0,
    locked_until            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by              UUID,

    CONSTRAINT pk_users            PRIMARY KEY (id),
    CONSTRAINT uq_users_username   UNIQUE (username),
    CONSTRAINT uq_users_email      UNIQUE (email),
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES auth.users (id)
        ON DELETE SET NULL
);

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- auth.user_roles
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.user_roles (
    user_id    UUID        NOT NULL,
    role_id    UUID        NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by UUID,

    CONSTRAINT pk_user_roles        PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user   FOREIGN KEY (user_id)    REFERENCES auth.users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role   FOREIGN KEY (role_id)    REFERENCES auth.roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_granter FOREIGN KEY (granted_by) REFERENCES auth.users (id) ON DELETE SET NULL
);

-- ----------------------------------------------------------------------------
-- auth.refresh_tokens
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL,
    token_hash         VARCHAR(255) NOT NULL,
    expires_at         TIMESTAMPTZ  NOT NULL,
    issued_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at         TIMESTAMPTZ,
    device_fingerprint VARCHAR(255),
    ip_address         INET,

    CONSTRAINT pk_refresh_tokens      PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES auth.users (id) ON DELETE CASCADE
);

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_users_store_id
    ON auth.users (store_id) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_users_is_active
    ON auth.users (is_active, username);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_active
    ON auth.refresh_tokens (user_id, expires_at) WHERE revoked_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires
    ON auth.refresh_tokens (expires_at) WHERE revoked_at IS NULL;
