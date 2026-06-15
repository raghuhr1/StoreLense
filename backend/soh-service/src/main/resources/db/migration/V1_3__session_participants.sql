-- Phase 5: multi-persona session participant tracking
SET search_path TO soh;

-- ---------------------------------------------------------------------------
-- soh.session_participants
-- One row per (session, device). Tracks who is scanning which zone and
-- whether they have finished their portion of the count.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS session_participants (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    session_id   UUID        NOT NULL,
    device_id    VARCHAR(64) NOT NULL,
    user_id      UUID        NOT NULL,
    zone_region  VARCHAR(100),
    status       VARCHAR(20) NOT NULL DEFAULT 'active',  -- active | done | abandoned
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,

    CONSTRAINT pk_session_participants         PRIMARY KEY (id),
    CONSTRAINT fk_session_participants_session FOREIGN KEY (session_id)
        REFERENCES soh_sessions(id) ON DELETE CASCADE,
    CONSTRAINT uq_session_participants_device  UNIQUE (session_id, device_id),
    CONSTRAINT ck_session_participants_status  CHECK (status IN ('active', 'done', 'abandoned'))
);

-- Partial unique index: at most one ACTIVE participant may claim a given zone
-- per session. Partial so that done/abandoned participants don't block re-use
-- of a zone by a later device.
CREATE UNIQUE INDEX IF NOT EXISTS uq_session_zone_active
    ON session_participants (session_id, zone_region)
    WHERE status = 'active' AND zone_region IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_session_participants_session
    ON session_participants (session_id, status);
