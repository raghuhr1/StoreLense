-- =============================================================================
-- StoreLense: Schema rfid
-- Owner: rfid-processing-service
-- rfid_reads is partitioned by read_at (monthly range).
-- =============================================================================

-- ----------------------------------------------------------------------------
-- rfid.rfid_sessions
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rfid.rfid_sessions (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    store_id         UUID         NOT NULL,
    soh_session_id   UUID,
    reader_id        UUID,
    device_id        VARCHAR(100),
    session_type     VARCHAR(20)  NOT NULL DEFAULT 'soh',
    status           VARCHAR(20)  NOT NULL DEFAULT 'open',
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at        TIMESTAMPTZ,
    total_read_count INT          NOT NULL DEFAULT 0,
    unique_epc_count INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_rfid_sessions         PRIMARY KEY (id),
    CONSTRAINT ck_rfid_sessions_type    CHECK (session_type IN ('soh', 'refill_verify', 'spot_check')),
    CONSTRAINT ck_rfid_sessions_status  CHECK (status IN ('open', 'closed', 'cancelled')),
    CONSTRAINT ck_rfid_sessions_reads   CHECK (total_read_count >= 0),
    CONSTRAINT ck_rfid_sessions_unique  CHECK (unique_epc_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_rfid_sessions_store
    ON rfid.rfid_sessions (store_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_rfid_sessions_soh
    ON rfid.rfid_sessions (soh_session_id) WHERE soh_session_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_rfid_sessions_open
    ON rfid.rfid_sessions (store_id, status, started_at)
    WHERE status = 'open';

-- ----------------------------------------------------------------------------
-- rfid.rfid_reads  (partitioned parent table)
-- BIGSERIAL id is local to each partition — use (rfid_session_id, id) for
-- cross-partition ordering. Clients should use read_at for time ordering.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rfid.rfid_reads (
    id               BIGINT       NOT NULL DEFAULT nextval('rfid.rfid_reads_id_seq'),
    rfid_session_id  UUID         NOT NULL,
    store_id         UUID         NOT NULL,
    reader_id        UUID,
    epc              VARCHAR(128) NOT NULL,
    rssi             NUMERIC(6,2),
    antenna_port     SMALLINT,
    read_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed        BOOLEAN      NOT NULL DEFAULT false,
    processed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_rfid_reads     PRIMARY KEY (id, read_at),
    CONSTRAINT ck_rfid_reads_epc CHECK (char_length(epc) >= 16)
) PARTITION BY RANGE (read_at);

-- Sequence for the partitioned table id
CREATE SEQUENCE IF NOT EXISTS rfid.rfid_reads_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE CACHE 100;

-- ----------------------------------------------------------------------------
-- Default partition for reads outside explicitly created month ranges
-- (safety net — should never receive data if monthly partitions are created ahead of time)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rfid.rfid_reads_default
    PARTITION OF rfid.rfid_reads DEFAULT;

-- ----------------------------------------------------------------------------
-- Initial monthly partitions — create for the current rollout window.
-- The fn_create_monthly_rfid_partitions() function (see 10_functions.sql)
-- should be called monthly via pg_cron.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    partition_start DATE;
    partition_end   DATE;
    partition_name  TEXT;
BEGIN
    FOR i IN 0..11 LOOP
        partition_start := date_trunc('month', now()) + (i || ' months')::INTERVAL;
        partition_end   := partition_start + INTERVAL '1 month';
        partition_name  := 'rfid_reads_' || to_char(partition_start, 'YYYY_MM');

        IF NOT EXISTS (
            SELECT 1 FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'rfid' AND c.relname = partition_name
        ) THEN
            EXECUTE format(
                'CREATE TABLE rfid.%I PARTITION OF rfid.rfid_reads
                 FOR VALUES FROM (%L) TO (%L)',
                partition_name, partition_start, partition_end
            );
            RAISE NOTICE 'Created partition: rfid.%', partition_name;
        END IF;
    END LOOP;
END;
$$;

-- ----------------------------------------------------------------------------
-- Indexes on the parent (inherited by child partitions automatically in PG 11+)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_rfid_reads_session
    ON rfid.rfid_reads (rfid_session_id, read_at);

CREATE INDEX IF NOT EXISTS idx_rfid_reads_store_epc
    ON rfid.rfid_reads (store_id, epc, read_at DESC);

CREATE INDEX IF NOT EXISTS idx_rfid_reads_unprocessed
    ON rfid.rfid_reads (processed, read_at)
    WHERE processed = false;
