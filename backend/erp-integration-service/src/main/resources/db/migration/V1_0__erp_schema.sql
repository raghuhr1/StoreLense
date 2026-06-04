-- ERP Integration Service schema
SET search_path TO erp;

CREATE TABLE IF NOT EXISTS erp_sync_log (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    sync_type         VARCHAR(30) NOT NULL,
    direction         VARCHAR(10) NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'running',
    started_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    records_fetched   INT         NOT NULL DEFAULT 0,
    records_published INT         NOT NULL DEFAULT 0,
    records_failed    INT         NOT NULL DEFAULT 0,
    error_message     TEXT,
    erp_cursor        VARCHAR(255),
    triggered_by      VARCHAR(50) NOT NULL DEFAULT 'scheduler',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_erp_sync_log   PRIMARY KEY (id),
    CONSTRAINT ck_erp_sync_dir   CHECK (direction IN ('INBOUND','OUTBOUND')),
    CONSTRAINT ck_erp_sync_status CHECK (status IN ('running','completed','partial','failed'))
);

CREATE INDEX IF NOT EXISTS idx_erp_sync_log_type_started
    ON erp_sync_log (sync_type, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_erp_sync_log_running
    ON erp_sync_log (sync_type, status) WHERE status = 'running';

CREATE TABLE IF NOT EXISTS erp_product_mapping (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    erp_product_code    VARCHAR(100) NOT NULL,
    internal_sku        VARCHAR(100) NOT NULL,
    internal_product_id UUID,
    erp_product_name    VARCHAR(500),
    erp_category_code   VARCHAR(50),
    last_synced_at      TIMESTAMPTZ,
    sync_hash           VARCHAR(64),
    is_active           BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_erp_product_mapping      PRIMARY KEY (id),
    CONSTRAINT uq_erp_product_code         UNIQUE (erp_product_code)
);

CREATE INDEX IF NOT EXISTS idx_erp_product_mapping_sku
    ON erp_product_mapping (internal_sku);

CREATE TABLE IF NOT EXISTS erp_store_mapping (
    id                      UUID        NOT NULL DEFAULT gen_random_uuid(),
    erp_store_code          VARCHAR(50) NOT NULL,
    internal_store_id       UUID        NOT NULL,
    inventory_sync_enabled  BOOLEAN     NOT NULL DEFAULT true,
    soh_push_enabled        BOOLEAN     NOT NULL DEFAULT true,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_erp_store_mapping     PRIMARY KEY (id),
    CONSTRAINT uq_erp_store_code        UNIQUE (erp_store_code),
    CONSTRAINT uq_erp_store_internal_id UNIQUE (internal_store_id)
);
