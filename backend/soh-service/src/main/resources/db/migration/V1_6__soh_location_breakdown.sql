-- Cycle Count Phase 1: location attribution on session items + per-location result breakdown
SET search_path TO soh;

-- ---------------------------------------------------------------------------
-- soh_session_items: tag each counted product row with its location/section.
-- Existing rows default to NULL — pre-cycle-count data is location-unaware.
-- ---------------------------------------------------------------------------
ALTER TABLE soh_session_items
    ADD COLUMN IF NOT EXISTS location_code VARCHAR(20),
    ADD COLUMN IF NOT EXISTS section_code  VARCHAR(20);

ALTER TABLE soh_session_items
    ADD CONSTRAINT ck_soh_items_location
        CHECK (location_code IS NULL
               OR location_code IN ('SALES_FLOOR', 'BACKROOM')),
    ADD CONSTRAINT ck_soh_items_section
        CHECK (section_code IS NULL
               OR (location_code = 'SALES_FLOOR'
                   AND section_code IN ('MENS', 'WOMENS', 'KIDS', 'FOOTWEAR', 'ACCESSORIES'))),
    ADD CONSTRAINT ck_soh_items_backroom_no_section
        CHECK (location_code <> 'BACKROOM' OR section_code IS NULL);

-- The existing unique key is (session_id, product_id, zone_id).
-- With location-aware counting a product can appear in both SALES_FLOOR and
-- BACKROOM in the same session, so add location to the uniqueness key.
ALTER TABLE soh_session_items
    DROP CONSTRAINT uq_soh_session_items_key;

ALTER TABLE soh_session_items
    ADD CONSTRAINT uq_soh_session_items_key
        UNIQUE (session_id, product_id, zone_id, location_code, section_code);

CREATE INDEX IF NOT EXISTS idx_soh_session_items_location
    ON soh_session_items (session_id, location_code);

-- ---------------------------------------------------------------------------
-- soh_results: per-location unit breakdown.
-- floor_variance and backroom_variance are computed on insert/update by the
-- application layer (not GENERATED columns) so they can be updated
-- incrementally without a full table rewrite.
-- ---------------------------------------------------------------------------
ALTER TABLE soh_results
    ADD COLUMN IF NOT EXISTS floor_units_counted     INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS floor_units_expected    INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS floor_variance          INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backroom_units_counted  INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backroom_units_expected INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS backroom_variance       INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS total_store_variance    INT NOT NULL DEFAULT 0;
