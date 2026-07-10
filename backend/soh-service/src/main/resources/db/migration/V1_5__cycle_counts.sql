-- Cycle Count Phase 1: parent cycle_count entity + soh_sessions extensions
SET search_path TO soh;

-- ---------------------------------------------------------------------------
-- soh.cycle_counts
-- Parent record grouping one or more sessions (e.g. a Sales Floor session
-- and a Backroom session) into a single count event.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cycle_counts (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id     UUID        NOT NULL,
    count_date   DATE        NOT NULL DEFAULT CURRENT_DATE,
    status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by   UUID        NOT NULL,
    notes        TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_cycle_counts        PRIMARY KEY (id),
    CONSTRAINT ck_cycle_counts_status CHECK (
        status IN ('DRAFT', 'RUNNING', 'COMPLETED', 'UPLOADED', 'RECONCILED', 'CLOSED')
    )
);

CREATE TRIGGER trg_cycle_counts_updated_at
    BEFORE UPDATE ON cycle_counts
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

CREATE INDEX IF NOT EXISTS idx_cycle_counts_store_status
    ON cycle_counts (store_id, status, count_date DESC);

-- ---------------------------------------------------------------------------
-- soh_sessions extensions
-- ---------------------------------------------------------------------------

-- 1. Link sessions to a parent cycle count (nullable — existing sessions
--    and non-cycle-count SOH sessions remain unaffected).
ALTER TABLE soh_sessions
    ADD COLUMN IF NOT EXISTS cycle_count_id UUID
        REFERENCES cycle_counts (id) ON DELETE SET NULL;

-- 2. Enforce location: SALES_FLOOR or BACKROOM.
ALTER TABLE soh_sessions
    ADD COLUMN IF NOT EXISTS location_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS section_code  VARCHAR(20);

ALTER TABLE soh_sessions
    ADD CONSTRAINT ck_soh_sessions_location
        CHECK (location_code IS NULL
               OR location_code IN ('SALES_FLOOR', 'BACKROOM')),
    ADD CONSTRAINT ck_soh_sessions_section
        CHECK (section_code IS NULL
               OR (location_code = 'SALES_FLOOR'
                   AND section_code IN ('MENS', 'WOMENS', 'KIDS', 'FOOTWEAR', 'ACCESSORIES'))),
    ADD CONSTRAINT ck_soh_sessions_backroom_no_section
        CHECK (location_code <> 'BACKROOM' OR section_code IS NULL);

-- 3. Extend the status lifecycle.
--    PostgreSQL requires dropping and recreating CHECK constraints.
ALTER TABLE soh_sessions DROP CONSTRAINT ck_soh_sessions_status;
ALTER TABLE soh_sessions
    ADD CONSTRAINT ck_soh_sessions_status CHECK (
        status IN (
            'created', 'in_progress', 'paused',
            'completed', 'uploaded', 'reconciled', 'closed',
            'cancelled', 'failed'
        )
    );

-- 4. Lifecycle timestamp columns.
ALTER TABLE soh_sessions
    ADD COLUMN IF NOT EXISTS paused_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS resumed_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS uploaded_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reconciled_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS closed_at      TIMESTAMPTZ;

-- Supporting index for cycle-count-scoped session queries.
CREATE INDEX IF NOT EXISTS idx_soh_sessions_cycle_count
    ON soh_sessions (cycle_count_id, status)
    WHERE cycle_count_id IS NOT NULL;
