-- Cycle-count reconciliation header + line items (Sprint 2)
SET search_path TO erp;

CREATE TABLE IF NOT EXISTS cc_reconciliation (
    id              UUID           NOT NULL DEFAULT gen_random_uuid(),
    session_id      UUID           NOT NULL,
    batch_id        UUID           NOT NULL,
    store_id        UUID           NOT NULL,
    run_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    total_expected  INT            NOT NULL DEFAULT 0,
    total_scanned   INT            NOT NULL DEFAULT 0,
    matched_count   INT            NOT NULL DEFAULT 0,
    missing_count   INT            NOT NULL DEFAULT 0,
    extra_count     INT            NOT NULL DEFAULT 0,
    accuracy_pct    NUMERIC(5, 2),
    status          VARCHAR(20)    NOT NULL DEFAULT 'RUNNING',
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_cc_reconciliation         PRIMARY KEY (id),
    CONSTRAINT fk_cc_reconciliation_batch   FOREIGN KEY (batch_id) REFERENCES erp_import_batch (id),
    CONSTRAINT ck_cc_reconciliation_status  CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_cc_reconciliation_store_run
    ON cc_reconciliation (store_id, run_at DESC);

CREATE INDEX IF NOT EXISTS idx_cc_reconciliation_session
    ON cc_reconciliation (session_id);

CREATE INDEX IF NOT EXISTS idx_cc_reconciliation_batch
    ON cc_reconciliation (batch_id);

CREATE TABLE IF NOT EXISTS cc_reconciliation_items (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    reconciliation_id UUID         NOT NULL,
    epc               VARCHAR(100) NOT NULL,
    ean               VARCHAR(30),
    status            VARCHAR(10)  NOT NULL,
    expected_qty      INT          NOT NULL DEFAULT 0,
    scanned_qty       INT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_cc_reconciliation_items        PRIMARY KEY (id),
    CONSTRAINT fk_cc_reconciliation_items_recon  FOREIGN KEY (reconciliation_id) REFERENCES cc_reconciliation (id),
    CONSTRAINT ck_cc_reconciliation_item_status  CHECK (status IN ('MATCH', 'MISSING', 'EXTRA'))
);

CREATE INDEX IF NOT EXISTS idx_cc_reconciliation_items_recon
    ON cc_reconciliation_items (reconciliation_id);

CREATE INDEX IF NOT EXISTS idx_cc_reconciliation_items_epc
    ON cc_reconciliation_items (epc);

CREATE INDEX IF NOT EXISTS idx_cc_reconciliation_items_status
    ON cc_reconciliation_items (reconciliation_id, status);
