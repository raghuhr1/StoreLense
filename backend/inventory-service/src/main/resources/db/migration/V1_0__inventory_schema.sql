-- =============================================================================
-- StoreLense: Schema inventory
-- Owner: inventory-service
-- No FK constraints to other schemas — cross-domain refs resolved by app layer.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- inventory.inventory_state
-- One row per store × product × zone.
-- NULL zone_id = store-level aggregate (no zone granularity).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory.inventory_state (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id             UUID        NOT NULL,
    product_id           UUID        NOT NULL,
    zone_id              UUID,
    quantity_on_hand     INT         NOT NULL DEFAULT 0,
    quantity_expected    INT         NOT NULL DEFAULT 0,
    last_counted_at      TIMESTAMPTZ,
    last_soh_session_id  UUID,
    accuracy_pct         NUMERIC(5,2),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_inventory_state       PRIMARY KEY (id),
    CONSTRAINT uq_inventory_state_key   UNIQUE (store_id, product_id, zone_id),
    CONSTRAINT ck_inventory_qty_on_hand CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_inventory_qty_expected CHECK (quantity_expected >= 0),
    CONSTRAINT ck_inventory_accuracy    CHECK (accuracy_pct IS NULL OR (accuracy_pct >= 0 AND accuracy_pct <= 999.99))
);

CREATE TRIGGER trg_inventory_state_updated_at
    BEFORE UPDATE ON inventory.inventory_state
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- inventory.epc_registry
-- Current lifecycle status of every EPC known at a store.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS inventory.epc_registry (
    id                       UUID         NOT NULL DEFAULT gen_random_uuid(),
    epc                      VARCHAR(128) NOT NULL,
    store_id                 UUID         NOT NULL,
    product_id               UUID,
    zone_id                  UUID,
    status                   VARCHAR(20)  NOT NULL DEFAULT 'in_store',
    last_seen_at             TIMESTAMPTZ,
    last_seen_by_reader_id   UUID,
    first_seen_at            TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_epc_registry          PRIMARY KEY (id),
    CONSTRAINT uq_epc_registry_key      UNIQUE (epc, store_id),
    CONSTRAINT ck_epc_registry_status   CHECK (status IN ('in_store', 'sold', 'missing', 'damaged', 'transferred'))
);

CREATE TRIGGER trg_epc_registry_updated_at
    BEFORE UPDATE ON inventory.epc_registry
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_inventory_state_store_product
    ON inventory.inventory_state (store_id, product_id);

CREATE INDEX IF NOT EXISTS idx_inventory_state_last_counted
    ON inventory.inventory_state (store_id, last_counted_at DESC);

CREATE INDEX IF NOT EXISTS idx_inventory_state_low_accuracy
    ON inventory.inventory_state (store_id, accuracy_pct)
    WHERE accuracy_pct IS NOT NULL AND accuracy_pct < 95.0;

CREATE INDEX IF NOT EXISTS idx_epc_registry_store_status
    ON inventory.epc_registry (store_id, status, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS idx_epc_registry_product
    ON inventory.epc_registry (product_id, store_id);

CREATE INDEX IF NOT EXISTS idx_epc_registry_missing
    ON inventory.epc_registry (store_id, last_seen_at)
    WHERE status = 'missing';
