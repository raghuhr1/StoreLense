-- Track gate-check completion status directly on the bill, so a bill that
-- was already released/flagged at the gate cannot be silently reopened for
-- a new RFID scan by the C66 guard app.

ALTER TABLE inventory.bills
    ADD COLUMN status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN gate_checked_at TIMESTAMPTZ;

ALTER TABLE inventory.bills
    ADD CONSTRAINT ck_bill_status CHECK (status IN ('PENDING', 'RELEASED', 'FLAGGED', 'ABANDONED'));

CREATE INDEX IF NOT EXISTS idx_bills_status
    ON inventory.bills (store_id, status);
