-- =============================================================================
-- StoreLense: Schema refill
-- Owner: refill-service
-- =============================================================================

-- ----------------------------------------------------------------------------
-- refill.refill_tasks
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refill.refill_tasks (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    store_id            UUID        NOT NULL,
    task_type           VARCHAR(20) NOT NULL DEFAULT 'replenishment',
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    priority            SMALLINT    NOT NULL DEFAULT 5,
    source              VARCHAR(20) NOT NULL DEFAULT 'manual',
    source_session_id   UUID,
    due_date            DATE,
    notes               TEXT,
    created_by          UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at        TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    cancellation_reason TEXT,

    CONSTRAINT pk_refill_tasks          PRIMARY KEY (id),
    CONSTRAINT ck_refill_tasks_type     CHECK (task_type IN ('replenishment', 'urgency', 'cycle_count')),
    CONSTRAINT ck_refill_tasks_status   CHECK (status IN ('pending', 'assigned', 'in_progress', 'completed', 'cancelled')),
    CONSTRAINT ck_refill_tasks_priority CHECK (priority BETWEEN 1 AND 10),
    CONSTRAINT ck_refill_tasks_source   CHECK (source IN ('manual', 'soh_trigger', 'scheduled', 'erp')),
    CONSTRAINT ck_refill_tasks_timing   CHECK (completed_at IS NULL OR completed_at >= created_at)
);

CREATE TRIGGER trg_refill_tasks_updated_at
    BEFORE UPDATE ON refill.refill_tasks
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- refill.refill_task_items
-- Individual product lines within a task.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refill.refill_task_items (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    task_id             UUID        NOT NULL,
    product_id          UUID        NOT NULL,
    zone_id             UUID,
    requested_quantity  INT         NOT NULL DEFAULT 0,
    fulfilled_quantity  INT         NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    skip_reason         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_refill_task_items         PRIMARY KEY (id),
    CONSTRAINT uq_refill_task_items_key     UNIQUE (task_id, product_id, zone_id),
    CONSTRAINT fk_refill_task_items_task    FOREIGN KEY (task_id) REFERENCES refill.refill_tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_refill_items_status       CHECK (status IN ('pending', 'partial', 'fulfilled', 'skipped')),
    CONSTRAINT ck_refill_items_req_qty      CHECK (requested_quantity >= 0),
    CONSTRAINT ck_refill_items_ful_qty      CHECK (fulfilled_quantity >= 0),
    CONSTRAINT ck_refill_items_skip_reason  CHECK (
        (status != 'skipped') OR (skip_reason IS NOT NULL)
    )
);

CREATE TRIGGER trg_refill_task_items_updated_at
    BEFORE UPDATE ON refill.refill_task_items
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- ----------------------------------------------------------------------------
-- refill.refill_assignments
-- One active assignment per task. Re-assignment inserts a new row and sets
-- the previous row status to 'reassigned'.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refill.refill_assignments (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    task_id      UUID        NOT NULL,
    assigned_to  UUID        NOT NULL,
    assigned_by  UUID        NOT NULL,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at  TIMESTAMPTZ,
    started_at   TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    status       VARCHAR(20) NOT NULL DEFAULT 'assigned',
    notes        TEXT,

    CONSTRAINT pk_refill_assignments        PRIMARY KEY (id),
    CONSTRAINT fk_refill_assignments_task   FOREIGN KEY (task_id) REFERENCES refill.refill_tasks (id) ON DELETE CASCADE,
    CONSTRAINT ck_refill_assignments_status CHECK (status IN ('assigned', 'accepted', 'in_progress', 'completed', 'reassigned')),
    CONSTRAINT ck_refill_assignments_timing CHECK (
        (accepted_at IS NULL OR accepted_at >= assigned_at) AND
        (started_at  IS NULL OR started_at  >= assigned_at) AND
        (completed_at IS NULL OR completed_at >= assigned_at)
    )
);

-- Only one active (non-reassigned) assignment per task at a time
CREATE UNIQUE INDEX IF NOT EXISTS uq_refill_assignments_active_task
    ON refill.refill_assignments (task_id)
    WHERE status NOT IN ('reassigned', 'completed');

-- ----------------------------------------------------------------------------
-- Indexes
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_refill_tasks_store_status
    ON refill.refill_tasks (store_id, status, priority ASC, due_date ASC);

CREATE INDEX IF NOT EXISTS idx_refill_tasks_created_by
    ON refill.refill_tasks (created_by, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_refill_tasks_due
    ON refill.refill_tasks (store_id, due_date)
    WHERE status NOT IN ('completed', 'cancelled');

CREATE INDEX IF NOT EXISTS idx_refill_task_items_task
    ON refill.refill_task_items (task_id);

CREATE INDEX IF NOT EXISTS idx_refill_task_items_product
    ON refill.refill_task_items (product_id);

CREATE INDEX IF NOT EXISTS idx_refill_assignments_user
    ON refill.refill_assignments (assigned_to, assigned_at DESC)
    WHERE status NOT IN ('reassigned', 'completed');

CREATE INDEX IF NOT EXISTS idx_refill_assignments_task
    ON refill.refill_assignments (task_id, assigned_at DESC);
