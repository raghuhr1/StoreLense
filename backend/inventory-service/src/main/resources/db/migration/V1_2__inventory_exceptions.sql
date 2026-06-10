-- Exceptions API schema: surface MISSING_EPC / GHOST_TAG events for investigation
-- (Block 9 prerequisite)

CREATE TABLE IF NOT EXISTS inventory.exception_events (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    store_id          UUID         NOT NULL,
    epc               VARCHAR(128) NOT NULL,
    type              VARCHAR(20)  NOT NULL,
    confidence_score  INT          NOT NULL DEFAULT 0,
    classification    VARCHAR(30),
    reasons           JSONB,
    last_seen_at      TIMESTAMPTZ,
    status            VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_exception_events              PRIMARY KEY (id),
    CONSTRAINT ck_exception_events_type         CHECK (type IN ('MISSING_EPC', 'GHOST_TAG', 'READ_MISS', 'UNDER_REVIEW')),
    CONSTRAINT ck_exception_events_class        CHECK (classification IS NULL OR classification IN (
                                                    'READ_MISS_LIKELY', 'ACTUALLY_MISSING',
                                                    'GHOST_SUSPECTED', 'CONFIRMED_GHOST')),
    CONSTRAINT ck_exception_events_confidence   CHECK (confidence_score >= 0 AND confidence_score <= 100),
    CONSTRAINT ck_exception_events_status       CHECK (status IN ('OPEN', 'IGNORED', 'INVESTIGATING', 'RESOLVED'))
);

CREATE TRIGGER trg_exception_events_updated_at
    BEFORE UPDATE ON inventory.exception_events
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

CREATE INDEX IF NOT EXISTS idx_exception_events_store_status
    ON inventory.exception_events (store_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_exception_events_epc
    ON inventory.exception_events (epc, store_id);

CREATE INDEX IF NOT EXISTS idx_exception_events_open
    ON inventory.exception_events (store_id, type, created_at DESC)
    WHERE status IN ('OPEN', 'INVESTIGATING');
