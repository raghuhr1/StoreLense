-- Cycle Count Phase 1: location + section taxonomy
-- Defines the valid locations (SALES_FLOOR, BACKROOM) and Sales Floor sections
-- (MENS, WOMENS, KIDS, FOOTWEAR, ACCESSORIES) per store.
-- All session and reconciliation records reference these codes.
SET search_path TO soh;

-- ---------------------------------------------------------------------------
-- soh.store_locations
-- One row per (store, location, section). BACKROOM rows always have
-- section_code = NULL; SALES_FLOOR rows may have a section_code.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS store_locations (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    store_id      UUID         NOT NULL,
    location_code VARCHAR(20)  NOT NULL,
    section_code  VARCHAR(20),
    display_name  VARCHAR(100) NOT NULL,
    sort_order    SMALLINT     NOT NULL DEFAULT 0,
    is_active     BOOLEAN      NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_store_locations          PRIMARY KEY (id),
    CONSTRAINT uq_store_locations_key      UNIQUE (store_id, location_code, section_code),
    CONSTRAINT ck_store_locations_loc      CHECK (location_code IN ('SALES_FLOOR', 'BACKROOM')),
    CONSTRAINT ck_store_locations_section  CHECK (
        section_code IS NULL
        OR (location_code = 'SALES_FLOOR'
            AND section_code IN ('MENS', 'WOMENS', 'KIDS', 'FOOTWEAR', 'ACCESSORIES'))
    ),
    -- BACKROOM must never have a section
    CONSTRAINT ck_store_locations_backroom_no_section
        CHECK (location_code <> 'BACKROOM' OR section_code IS NULL)
);

CREATE INDEX IF NOT EXISTS idx_store_locations_store
    ON store_locations (store_id, is_active, location_code);
