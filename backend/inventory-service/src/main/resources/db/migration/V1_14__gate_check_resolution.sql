-- ============================================================
-- Flagged gate check resolution — how a guard/manager closed out
-- a FLAGGED release (unbilled/extra items found at the gate).
-- ============================================================

ALTER TABLE inventory.gate_checks
    ADD COLUMN IF NOT EXISTS resolution     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS resolved_by    UUID,
    ADD COLUMN IF NOT EXISTS resolved_at    TIMESTAMPTZ,
    ADD CONSTRAINT ck_gate_resolution CHECK (
        resolution IS NULL OR resolution IN ('CUSTOMER_VERIFIED', 'THEFT_PREVENTED', 'ESCALATED')
    );
