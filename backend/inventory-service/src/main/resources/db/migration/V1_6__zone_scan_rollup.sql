-- Zone scan rollup: persisted snapshot of RFID scan vs par-level comparison.
-- One row per store+zone+product per session. status drives replenishment urgency.
CREATE TABLE IF NOT EXISTS inventory.zone_scan_rollup (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id     UUID        NOT NULL,
    zone_id      UUID        NOT NULL,
    product_id   UUID        NOT NULL,
    session_id   UUID,
    scanned_qty  INTEGER     NOT NULL DEFAULT 0,
    par_qty      INTEGER     NOT NULL DEFAULT 0,
    min_qty      INTEGER     NOT NULL DEFAULT 0,
    variance     INTEGER     NOT NULL DEFAULT 0,   -- scanned_qty - par_qty (negative = shortage)
    status       VARCHAR(20) NOT NULL DEFAULT 'ok', -- ok | low | critical | surplus
    computed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_zone_scan_rollup PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_zone_scan_rollup_store_session
    ON inventory.zone_scan_rollup (store_id, session_id, computed_at DESC);

CREATE INDEX IF NOT EXISTS idx_zone_scan_rollup_status
    ON inventory.zone_scan_rollup (store_id, status);
