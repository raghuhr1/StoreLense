-- Transfers schema (Block 10 prerequisite)

CREATE TABLE IF NOT EXISTS soh.transfers (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    source_store_id  UUID        NOT NULL,
    dest_store_id    UUID        NOT NULL,
    type             VARCHAR(30) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_by       UUID        NOT NULL,
    epcs             JSONB       NOT NULL DEFAULT '[]',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_at      TIMESTAMPTZ,
    received_epcs    JSONB,

    CONSTRAINT pk_transfers                 PRIMARY KEY (id),
    CONSTRAINT ck_transfers_status          CHECK (status IN ('PENDING', 'IN_TRANSIT', 'RECEIVED', 'CANCELLED')),
    CONSTRAINT ck_transfers_stores_differ   CHECK (source_store_id <> dest_store_id),
    CONSTRAINT ck_transfers_received_timing CHECK (received_at IS NULL OR received_at >= created_at)
);

CREATE TRIGGER trg_transfers_updated_at
    BEFORE UPDATE ON soh.transfers
    FOR EACH ROW EXECUTE FUNCTION public.fn_set_updated_at();

CREATE INDEX IF NOT EXISTS idx_transfers_source_store
    ON soh.transfers (source_store_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transfers_dest_store
    ON soh.transfers (dest_store_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_transfers_active
    ON soh.transfers (source_store_id, status, created_at DESC)
    WHERE status IN ('PENDING', 'IN_TRANSIT');
