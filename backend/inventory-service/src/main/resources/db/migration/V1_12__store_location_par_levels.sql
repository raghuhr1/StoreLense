-- Store Location Par Levels: target Sales Floor quantities per product per store.
-- Replaces zone_par_levels for the SOH-based (Sales Floor / Backroom) replenishment model.
-- par_qty = ideal quantity that should be on the Sales Floor (replenishment trigger threshold).
-- min_qty = critical low-stock threshold (urgent replenishment).
CREATE TABLE IF NOT EXISTS inventory.store_location_par_levels (
    id             UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id       UUID        NOT NULL,
    location_code  VARCHAR(20) NOT NULL DEFAULT 'SALES_FLOOR' CHECK (location_code IN ('SALES_FLOOR','BACKROOM')),
    product_id     UUID        NOT NULL,
    par_qty        INTEGER     NOT NULL DEFAULT 1 CHECK (par_qty >= 0),
    min_qty        INTEGER     NOT NULL DEFAULT 0 CHECK (min_qty >= 0),
    active         BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_store_location_par_levels PRIMARY KEY (id),
    CONSTRAINT uq_store_location_par_levels UNIQUE (store_id, location_code, product_id)
);

CREATE INDEX IF NOT EXISTS idx_store_location_par_levels_store_loc
    ON inventory.store_location_par_levels (store_id, location_code);
