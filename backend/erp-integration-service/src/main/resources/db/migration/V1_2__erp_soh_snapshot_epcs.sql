-- EPC-level resolution records for SOH snapshots (Sprint 1)
SET search_path TO erp;

CREATE TABLE IF NOT EXISTS erp_soh_snapshot_epcs (
    id          UUID         NOT NULL DEFAULT gen_random_uuid(),
    snapshot_id UUID         NOT NULL,
    epc         VARCHAR(100) NOT NULL,
    matched_by  VARCHAR(30)  NOT NULL,
    resolved_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_erp_soh_snapshot_epcs        PRIMARY KEY (id),
    CONSTRAINT fk_erp_soh_snapshot_epcs_snap   FOREIGN KEY (snapshot_id) REFERENCES erp_soh_snapshot (id),
    CONSTRAINT uq_erp_soh_snapshot_epcs_epc    UNIQUE (snapshot_id, epc),
    CONSTRAINT ck_erp_soh_snapshot_matched_by  CHECK (matched_by IN ('SGTIN96', 'INBOUND_COMMISSION', 'MANUAL'))
);

CREATE INDEX IF NOT EXISTS idx_erp_soh_snapshot_epcs_snap
    ON erp_soh_snapshot_epcs (snapshot_id);

CREATE INDEX IF NOT EXISTS idx_erp_soh_snapshot_epcs_epc
    ON erp_soh_snapshot_epcs (epc);
