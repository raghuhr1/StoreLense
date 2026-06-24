-- Replenishment rules: per-store triggers for auto-creating refill tasks.
-- trigger_status 'low'      → fires for both 'low' and 'critical' rollup rows.
-- trigger_status 'critical' → fires only for 'critical' rollup rows.
-- Unique per (store_id, trigger_status) so each store has at most one low-rule
-- and one critical-rule.
CREATE TABLE IF NOT EXISTS inventory.replenishment_rules (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id         UUID        NOT NULL,
    trigger_status   VARCHAR(20) NOT NULL DEFAULT 'low',
    priority         SMALLINT    NOT NULL DEFAULT 5
                                 CHECK (priority BETWEEN 1 AND 10),
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_replenishment_rules PRIMARY KEY (id),
    CONSTRAINT uq_replenishment_rules UNIQUE (store_id, trigger_status),
    CONSTRAINT ck_replenishment_rules_status
        CHECK (trigger_status IN ('low', 'critical'))
);
