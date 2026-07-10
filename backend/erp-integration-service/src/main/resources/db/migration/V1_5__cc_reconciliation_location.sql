-- Cycle Count Phase 1: reconciliation location breakdown + multi-session + approval
SET search_path TO erp;

-- ---------------------------------------------------------------------------
-- cc_reconciliation: support cycle-count-scoped (multi-session) reconciliation
-- and per-location variance breakdown.
-- ---------------------------------------------------------------------------

-- Link reconciliation to the parent cycle count.
-- session_id is relaxed to nullable so a single reconciliation can cover
-- all sessions under a cycle_count_id without being pinned to one session.
ALTER TABLE cc_reconciliation
    ALTER COLUMN session_id DROP NOT NULL;

ALTER TABLE cc_reconciliation
    ADD COLUMN IF NOT EXISTS cycle_count_id UUID,
    ADD COLUMN IF NOT EXISTS floor_expected     INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS floor_scanned      INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS floor_missing      INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backroom_expected  INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backroom_scanned   INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backroom_missing   INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS reviewer_id        UUID,
    ADD COLUMN IF NOT EXISTS approved_at        TIMESTAMPTZ;

-- Extend the status lifecycle to include an approval gate.
ALTER TABLE cc_reconciliation DROP CONSTRAINT ck_cc_reconciliation_status;
ALTER TABLE cc_reconciliation
    ADD CONSTRAINT ck_cc_reconciliation_status
        CHECK (status IN ('RUNNING', 'COMPLETED', 'PENDING_APPROVAL', 'APPROVED', 'FAILED'));

-- A reconciliation must be anchored to either a single session or a cycle count.
ALTER TABLE cc_reconciliation
    ADD CONSTRAINT ck_cc_reconciliation_anchor
        CHECK (session_id IS NOT NULL OR cycle_count_id IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_cc_reconciliation_cycle_count
    ON cc_reconciliation (cycle_count_id)
    WHERE cycle_count_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- cc_reconciliation_items: tag each line item with where the EPC was found.
-- ---------------------------------------------------------------------------
ALTER TABLE cc_reconciliation_items
    ADD COLUMN IF NOT EXISTS location_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS section_code  VARCHAR(20);

ALTER TABLE cc_reconciliation_items
    ADD CONSTRAINT ck_cc_recon_items_location
        CHECK (location_code IS NULL
               OR location_code IN ('SALES_FLOOR', 'BACKROOM')),
    ADD CONSTRAINT ck_cc_recon_items_section
        CHECK (section_code IS NULL
               OR (location_code = 'SALES_FLOOR'
                   AND section_code IN ('MENS', 'WOMENS', 'KIDS', 'FOOTWEAR', 'ACCESSORIES'))),
    ADD CONSTRAINT ck_cc_recon_items_backroom_no_section
        CHECK (location_code <> 'BACKROOM' OR section_code IS NULL);

CREATE INDEX IF NOT EXISTS idx_cc_recon_items_location
    ON cc_reconciliation_items (reconciliation_id, location_code);
