-- =============================================================================
-- StoreLense: Schema soh
-- Owner: soh-service
-- =============================================================================

-- ----------------------------------------------------------------------------
-- soh.soh_sessions
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS soh.soh_sessions (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id            UUID        NOT NULL,
    zone_id             UUID,
    session_type        VARCHAR(20) NOT NULL DEFAULT 'manual',
    status              VARCHAR(20) NOT NULL DEFAULT 'created',
    started_by          UUID        NOT NULL,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason TEXT,
    total_epc_reads     INT         NOT NULL DEFAULT 0,
    unique_epc_count    INT         NOT NULL DEFAULT 0,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_soh_sessions          PRIMARY KEY (id),
    CONSTRAINT ck_soh_sessions_type     CHECK (session_type IN ('manual', 'scheduled', 'full_store', 'spot_check')),
    CONSTRAINT ck_soh_sessions_status   CHECK (status IN ('created', 'in_progress', 'completed', 'cancelled', 'failed')),
    CONSTRAINT ck_soh_sessions_reads    CHECK (total_epc_reads >= 0),
    CONSTRAINT ck_soh_sessions_unique   CHECK (unique_epc_count >= 0),
    -- completed_at must be after started_at
    CONSTRAINT ck_soh_sessions_timing   CHECK (completed_at IS NULL OR completed_at >= started_at)
);

CREATE TRIGGER trg_soh_sessions_updated_at
    BEFORE UPDATE ON soh.soh_sessions
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- soh.soh_session_items
-- One row per product (× zone) seen during a session.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS soh.soh_session_items (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    session_id        UUID        NOT NULL,
    product_id        UUID        NOT NULL,
    zone_id           UUID,
    counted_quantity  INT         NOT NULL DEFAULT 0,
    expected_quantity INT         NOT NULL DEFAULT 0,
    variance          INT         GENERATED ALWAYS AS (counted_quantity - expected_quantity) STORED,
    variance_pct      NUMERIC(5,2),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_soh_session_items         PRIMARY KEY (id),
    CONSTRAINT uq_soh_session_items_key     UNIQUE (session_id, product_id, zone_id),
    CONSTRAINT fk_soh_session_items_session FOREIGN KEY (session_id) REFERENCES soh.soh_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_soh_items_counted         CHECK (counted_quantity >= 0),
    CONSTRAINT ck_soh_items_expected        CHECK (expected_quantity >= 0)
);

CREATE TRIGGER trg_soh_session_items_updated_at
    BEFORE UPDATE ON soh.soh_session_items
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- soh.soh_results
-- Aggregated outcome record — one per session.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS soh.soh_results (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    session_id              UUID        NOT NULL,
    store_id                UUID        NOT NULL,
    total_products_counted  INT         NOT NULL DEFAULT 0,
    total_units_counted     INT         NOT NULL DEFAULT 0,
    total_units_expected    INT         NOT NULL DEFAULT 0,
    accuracy_pct            NUMERIC(5,2),
    variance_count          INT         NOT NULL DEFAULT 0,
    overcount_items         INT         NOT NULL DEFAULT 0,
    undercount_items        INT         NOT NULL DEFAULT 0,
    result_generated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_soh_results           PRIMARY KEY (id),
    CONSTRAINT uq_soh_results_session   UNIQUE (session_id),
    CONSTRAINT fk_soh_results_session   FOREIGN KEY (session_id) REFERENCES soh.soh_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_soh_results_accuracy  CHECK (accuracy_pct IS NULL OR (accuracy_pct >= 0 AND accuracy_pct <= 999.99))
);

-- ----------------------------------------------------------------------------
-- soh.soh_variance
-- Detail rows for products with non-zero variance. Used for investigation.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS soh.soh_variance (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    session_id              UUID        NOT NULL,
    result_id               UUID        NOT NULL,
    store_id                UUID        NOT NULL,
    product_id              UUID        NOT NULL,
    zone_id                 UUID,
    counted_qty             INT         NOT NULL,
    expected_qty            INT         NOT NULL,
    variance_qty            INT         NOT NULL,
    variance_pct            NUMERIC(5,2),
    variance_type           VARCHAR(15) NOT NULL,
    requires_investigation  BOOLEAN     NOT NULL DEFAULT false,
    investigation_notes     TEXT,
    resolved_at             TIMESTAMPTZ,
    resolved_by             UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_soh_variance          PRIMARY KEY (id),
    CONSTRAINT fk_soh_variance_session  FOREIGN KEY (session_id) REFERENCES soh.soh_sessions (id),
    CONSTRAINT fk_soh_variance_result   FOREIGN KEY (result_id)  REFERENCES soh.soh_results  (id),
    CONSTRAINT ck_soh_variance_type     CHECK (variance_type IN ('overcount', 'undercount', 'match')),
    -- variance_qty must match counted - expected
    CONSTRAINT ck_soh_variance_qty      CHECK (variance_qty = counted_qty - expected_qty)
);

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_soh_sessions_store_status
    ON soh.soh_sessions (store_id, status, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_soh_sessions_started_by
    ON soh.soh_sessions (started_by, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_soh_sessions_active
    ON soh.soh_sessions (store_id, started_at DESC)
    WHERE status IN ('created', 'in_progress');

CREATE INDEX IF NOT EXISTS idx_soh_session_items_session
    ON soh.soh_session_items (session_id);

CREATE INDEX IF NOT EXISTS idx_soh_session_items_product
    ON soh.soh_session_items (product_id, session_id);

CREATE INDEX IF NOT EXISTS idx_soh_results_store
    ON soh.soh_results (store_id, result_generated_at DESC);

CREATE INDEX IF NOT EXISTS idx_soh_variance_session
    ON soh.soh_variance (session_id);

CREATE INDEX IF NOT EXISTS idx_soh_variance_store_date
    ON soh.soh_variance (store_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_soh_variance_investigation
    ON soh.soh_variance (store_id, requires_investigation)
    WHERE requires_investigation = true AND resolved_at IS NULL;
