-- ERP import batch + SOH snapshot tables (Sprint 1)
SET search_path TO erp;

CREATE TABLE IF NOT EXISTS erp_import_batch (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    store_id         UUID         NOT NULL,
    source_type      VARCHAR(10)  NOT NULL,
    file_path        TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_rows       INT          NOT NULL DEFAULT 0,
    resolved_rows    INT          NOT NULL DEFAULT 0,
    unresolved_rows  INT          NOT NULL DEFAULT 0,
    imported_at      TIMESTAMPTZ,
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_erp_import_batch        PRIMARY KEY (id),
    CONSTRAINT ck_erp_import_source_type  CHECK (source_type IN ('FILE', 'S3')),
    CONSTRAINT ck_erp_import_status       CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_erp_import_batch_store_created
    ON erp_import_batch (store_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_erp_import_batch_active
    ON erp_import_batch (status) WHERE status IN ('PENDING', 'PROCESSING');

CREATE TABLE IF NOT EXISTS erp_soh_snapshot (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    batch_id          UUID         NOT NULL,
    ean               VARCHAR(30)  NOT NULL,
    expected_qty      INT          NOT NULL DEFAULT 0,
    zone_region       VARCHAR(100),
    resolution_status VARCHAR(20)  NOT NULL DEFAULT 'RAW',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_erp_soh_snapshot          PRIMARY KEY (id),
    CONSTRAINT fk_erp_soh_snapshot_batch    FOREIGN KEY (batch_id) REFERENCES erp_import_batch (id),
    CONSTRAINT ck_erp_soh_resolution_status CHECK (resolution_status IN ('RAW', 'RESOLVED', 'PARTIAL', 'UNRESOLVED'))
);

CREATE INDEX IF NOT EXISTS idx_erp_soh_snapshot_batch
    ON erp_soh_snapshot (batch_id);

CREATE INDEX IF NOT EXISTS idx_erp_soh_snapshot_ean
    ON erp_soh_snapshot (ean);

CREATE INDEX IF NOT EXISTS idx_erp_soh_snapshot_resolution
    ON erp_soh_snapshot (resolution_status) WHERE resolution_status IN ('RAW', 'UNRESOLVED');
