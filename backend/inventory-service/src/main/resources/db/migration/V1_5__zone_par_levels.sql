-- Zone Par Levels: target floor quantities per product per zone.
-- par_qty = ideal quantity that should be on the floor (replenishment trigger threshold).
-- min_qty = critical low-stock threshold (urgent replenishment).
CREATE TABLE IF NOT EXISTS inventory.zone_par_levels (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id    UUID        NOT NULL,
    zone_id     UUID        NOT NULL,
    product_id  UUID        NOT NULL,
    par_qty     INTEGER     NOT NULL DEFAULT 1 CHECK (par_qty >= 0),
    min_qty     INTEGER     NOT NULL DEFAULT 0 CHECK (min_qty >= 0),
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_zone_par_levels   PRIMARY KEY (id),
    CONSTRAINT uq_zone_par_levels   UNIQUE (store_id, zone_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_zone_par_levels_store_zone
    ON inventory.zone_par_levels (store_id, zone_id);
