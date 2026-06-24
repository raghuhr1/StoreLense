-- =============================================================================
-- EPC Position History — append-only ledger of every zone/status change.
-- Extended EPC status values for inbound and transit lifecycle.
-- =============================================================================

-- Extend epc_registry to support new lifecycle statuses.
ALTER TABLE inventory.epc_registry DROP CONSTRAINT IF EXISTS ck_epc_registry_status;
ALTER TABLE inventory.epc_registry ADD CONSTRAINT ck_epc_registry_status
    CHECK (status IN (
        'in_store', 'sold', 'missing', 'damaged', 'transferred',
        'inbound', 'in_transit', 'unlocated'
    ));

-- Append-only ledger: one row per zone or status transition.
-- Never updated or deleted — this is the source-of-truth audit trail.
CREATE TABLE IF NOT EXISTS inventory.epc_position_history (
    id           UUID         NOT NULL DEFAULT gen_random_uuid(),
    epc          VARCHAR(128) NOT NULL,
    store_id     UUID         NOT NULL,
    product_id   UUID,
    from_zone_id UUID,
    to_zone_id   UUID,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20)  NOT NULL,
    moved_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    triggered_by VARCHAR(50)  NOT NULL DEFAULT 'scan_session',
    session_id   UUID,

    CONSTRAINT pk_epc_position_history PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_epc_pos_hist_epc_store
    ON inventory.epc_position_history (epc, store_id, moved_at DESC);

CREATE INDEX IF NOT EXISTS idx_epc_pos_hist_store_recent
    ON inventory.epc_position_history (store_id, moved_at DESC);

CREATE INDEX IF NOT EXISTS idx_epc_pos_hist_session
    ON inventory.epc_position_history (session_id)
    WHERE session_id IS NOT NULL;
