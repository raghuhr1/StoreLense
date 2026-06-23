-- Allow system-driven reconciliation (no ERP batch required)
SET search_path TO erp;

-- batch_id is optional when reconciliation is driven by epc_registry, not an ERP import
ALTER TABLE cc_reconciliation DROP CONSTRAINT IF EXISTS fk_cc_reconciliation_batch;
ALTER TABLE cc_reconciliation ALTER COLUMN batch_id DROP NOT NULL;

-- mode distinguishes how expected EPCs were sourced
ALTER TABLE cc_reconciliation
    ADD COLUMN IF NOT EXISTS mode VARCHAR(20) NOT NULL DEFAULT 'ERP_DRIVEN';

ALTER TABLE cc_reconciliation
    ADD CONSTRAINT ck_cc_reconciliation_mode CHECK (mode IN ('ERP_DRIVEN', 'SYSTEM_DRIVEN'));
