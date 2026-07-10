-- Maps a physical RFID antenna port to a store location (SALES_FLOOR / BACKROOM)
-- and optional section (MENS / WOMENS / KIDS / FOOTWEAR / ACCESSORIES).
-- Used by LocationMappingService (cached in Redis) so the processing pipeline
-- can enrich each read with locationCode/sectionCode without an HTTP round-trip.

CREATE TABLE IF NOT EXISTS rfid.antenna_location_mappings (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    store_id      UUID        NOT NULL,
    reader_id     VARCHAR(64) NOT NULL,
    antenna_port  SMALLINT    NOT NULL,
    location_code VARCHAR(20) NOT NULL
        CONSTRAINT alm_location_check CHECK (location_code IN ('SALES_FLOOR','BACKROOM')),
    section_code  VARCHAR(20)
        CONSTRAINT alm_section_check  CHECK (section_code IN ('MENS','WOMENS','KIDS','FOOTWEAR','ACCESSORIES')),
    -- BACKROOM antennas must not have a section
    CONSTRAINT alm_backroom_no_section CHECK (
        location_code <> 'BACKROOM' OR section_code IS NULL
    ),
    display_name  VARCHAR(100),
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_antenna_location UNIQUE (store_id, reader_id, antenna_port)
);

CREATE INDEX idx_antenna_location_store ON rfid.antenna_location_mappings (store_id);
CREATE INDEX idx_antenna_location_reader ON rfid.antenna_location_mappings (reader_id, antenna_port);

CREATE OR REPLACE TRIGGER trg_antenna_location_mappings_updated_at
    BEFORE UPDATE ON rfid.antenna_location_mappings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
