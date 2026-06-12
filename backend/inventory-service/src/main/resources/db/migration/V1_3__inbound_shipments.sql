-- =============================================================================
-- Inbound shipment tracking (DC → Store receiving)
-- =============================================================================

CREATE TABLE IF NOT EXISTS inventory.inbound_shipments (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id         UUID        NOT NULL,
    dc_code          VARCHAR(50),
    reference_number VARCHAR(100),
    status           VARCHAR(30) NOT NULL DEFAULT 'expected',
    expected_at      TIMESTAMPTZ,
    received_at      TIMESTAMPTZ,
    line_count       INT         NOT NULL DEFAULT 0,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_inbound_shipments PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_inbound_shipments_store_status
    ON inventory.inbound_shipments (store_id, status);
