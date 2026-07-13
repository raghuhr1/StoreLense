-- ============================================================
-- Gate check events — recorded by C66 gate app on each release
-- ============================================================

CREATE TABLE IF NOT EXISTS inventory.gate_checks (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id        UUID         NOT NULL,
    guard_user_id   UUID,
    bill_ref        VARCHAR(100),
    checked_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expected_count  INT          NOT NULL DEFAULT 0,
    matched_count   INT          NOT NULL DEFAULT 0,
    extra_count     INT          NOT NULL DEFAULT 0,
    outcome         VARCHAR(20)  NOT NULL DEFAULT 'RELEASED',
    epcs_matched    TEXT[]       NOT NULL DEFAULT '{}',
    epcs_extra      TEXT[]       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_gate_outcome CHECK (outcome IN ('RELEASED','FLAGGED','ABANDONED'))
);

CREATE INDEX IF NOT EXISTS idx_gate_checks_store_date
    ON inventory.gate_checks (store_id, checked_at DESC);

CREATE INDEX IF NOT EXISTS idx_gate_checks_outcome
    ON inventory.gate_checks (store_id, outcome, checked_at DESC);
