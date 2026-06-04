-- =============================================================================
-- StoreLense: Schema reporting
-- Owner: reporting-service
-- =============================================================================

-- ----------------------------------------------------------------------------
-- reporting.kpi_daily
-- Populated nightly by reporting-service aggregation job.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.kpi_daily (
    id                          UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id                    UUID        NOT NULL,
    kpi_date                    DATE        NOT NULL,
    inventory_accuracy_pct      NUMERIC(5,2),
    soh_sessions_count          INT         NOT NULL DEFAULT 0,
    refill_tasks_created        INT         NOT NULL DEFAULT 0,
    refill_tasks_completed      INT         NOT NULL DEFAULT 0,
    refill_completion_rate_pct  NUMERIC(5,2),
    avg_refill_time_minutes     NUMERIC(8,2),
    total_epc_reads             BIGINT      NOT NULL DEFAULT 0,
    unique_skus_counted         INT         NOT NULL DEFAULT 0,
    variance_items_count        INT         NOT NULL DEFAULT 0,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_kpi_daily         PRIMARY KEY (id),
    CONSTRAINT uq_kpi_daily_key     UNIQUE (store_id, kpi_date),
    CONSTRAINT ck_kpi_daily_date    CHECK (kpi_date <= CURRENT_DATE)
);

-- ----------------------------------------------------------------------------
-- reporting.report_snapshots
-- Metadata record for every generated report file stored in S3.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reporting.report_snapshots (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    store_id       UUID,
    report_type    VARCHAR(50)  NOT NULL,
    report_date    DATE         NOT NULL,
    generated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    generated_by   UUID,
    status         VARCHAR(20)  NOT NULL DEFAULT 'pending',
    file_key       VARCHAR(500),
    file_format    VARCHAR(10),
    file_size_bytes BIGINT,
    parameters     JSONB,
    error_message  TEXT,

    CONSTRAINT pk_report_snapshots          PRIMARY KEY (id),
    CONSTRAINT ck_report_snapshots_type     CHECK (report_type IN (
        'soh_accuracy', 'refill_compliance', 'inventory_variance',
        'store_kpi', 'executive_summary', 'epc_audit'
    )),
    CONSTRAINT ck_report_snapshots_status   CHECK (status IN ('pending', 'generating', 'completed', 'failed')),
    CONSTRAINT ck_report_snapshots_format   CHECK (file_format IS NULL OR file_format IN ('pdf', 'csv', 'xlsx')),
    CONSTRAINT ck_report_snapshots_file     CHECK (
        (status = 'completed' AND file_key IS NOT NULL) OR status != 'completed'
    )
);

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_kpi_daily_store_date
    ON reporting.kpi_daily (store_id, kpi_date DESC);

CREATE INDEX IF NOT EXISTS idx_kpi_daily_date
    ON reporting.kpi_daily (kpi_date DESC);

CREATE INDEX IF NOT EXISTS idx_kpi_daily_accuracy
    ON reporting.kpi_daily (kpi_date DESC, inventory_accuracy_pct)
    WHERE inventory_accuracy_pct IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_report_snapshots_store
    ON reporting.report_snapshots (store_id, report_date DESC, report_type);

CREATE INDEX IF NOT EXISTS idx_report_snapshots_status
    ON reporting.report_snapshots (status, generated_at)
    WHERE status IN ('pending', 'generating');
