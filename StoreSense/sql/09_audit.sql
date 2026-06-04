-- =============================================================================
-- StoreLense: Schema audit
-- Cross-cutting append-only audit log.
-- Partitioned monthly. Never updated or deleted.
-- =============================================================================

-- ----------------------------------------------------------------------------
-- audit.audit_log  (partitioned parent)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit.audit_log (
    id             BIGINT      NOT NULL DEFAULT nextval('audit.audit_log_id_seq'),
    event_time     TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id        UUID,
    store_id       UUID,
    schema_name    VARCHAR(50) NOT NULL,
    table_name     VARCHAR(100) NOT NULL,
    operation      VARCHAR(10) NOT NULL,
    record_id      UUID,
    old_values     JSONB,
    new_values     JSONB,
    ip_address     INET,
    correlation_id VARCHAR(100),

    CONSTRAINT pk_audit_log         PRIMARY KEY (id, event_time),
    CONSTRAINT ck_audit_operation   CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE'))
) PARTITION BY RANGE (event_time);

CREATE SEQUENCE IF NOT EXISTS audit.audit_log_id_seq
    START WITH 1 INCREMENT BY 1 NO MAXVALUE CACHE 100;

-- Default partition (safety net)
CREATE TABLE IF NOT EXISTS audit.audit_log_default
    PARTITION OF audit.audit_log DEFAULT;

-- Create 12 monthly partitions starting from current month
DO $$
DECLARE
    partition_start DATE;
    partition_end   DATE;
    partition_name  TEXT;
BEGIN
    FOR i IN 0..11 LOOP
        partition_start := date_trunc('month', now()) + (i || ' months')::INTERVAL;
        partition_end   := partition_start + INTERVAL '1 month';
        partition_name  := 'audit_log_' || to_char(partition_start, 'YYYY_MM');

        IF NOT EXISTS (
            SELECT 1 FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'audit' AND c.relname = partition_name
        ) THEN
            EXECUTE format(
                'CREATE TABLE audit.%I PARTITION OF audit.audit_log
                 FOR VALUES FROM (%L) TO (%L)',
                partition_name, partition_start, partition_end
            );
        END IF;
    END LOOP;
END;
$$;

-- ----------------------------------------------------------------------------
-- Indexes on parent (inherited by partitions)
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_audit_log_record
    ON audit.audit_log (schema_name, table_name, record_id, event_time DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_user
    ON audit.audit_log (user_id, event_time DESC)
    WHERE user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_log_store
    ON audit.audit_log (store_id, event_time DESC)
    WHERE store_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- Generic audit trigger function
-- Attach to any table that requires auditing via:
--   CREATE TRIGGER trg_audit_<table>
--       AFTER INSERT OR UPDATE OR DELETE ON <schema>.<table>
--       FOR EACH ROW EXECUTE FUNCTION audit.fn_audit_trigger();
-- The trigger reads current_setting() values injected by the app layer
-- (Spring Security context → SET LOCAL audit.user_id = '...', etc.)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION audit.fn_audit_trigger()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER AS $$
DECLARE
    v_user_id        UUID;
    v_store_id       UUID;
    v_correlation_id VARCHAR(100);
    v_record_id      UUID;
    v_old_values     JSONB;
    v_new_values     JSONB;
    v_ip             INET;
BEGIN
    -- Read session-local variables set by the application layer
    BEGIN
        v_user_id        := current_setting('audit.user_id', true)::UUID;
    EXCEPTION WHEN others THEN v_user_id := NULL;
    END;
    BEGIN
        v_store_id       := current_setting('audit.store_id', true)::UUID;
    EXCEPTION WHEN others THEN v_store_id := NULL;
    END;
    BEGIN
        v_correlation_id := current_setting('audit.correlation_id', true);
    EXCEPTION WHEN others THEN v_correlation_id := NULL;
    END;
    BEGIN
        v_ip := current_setting('audit.ip_address', true)::INET;
    EXCEPTION WHEN others THEN v_ip := NULL;
    END;

    IF TG_OP = 'INSERT' THEN
        v_record_id  := (NEW.id)::UUID;
        v_new_values := to_jsonb(NEW);
        v_old_values := NULL;
    ELSIF TG_OP = 'UPDATE' THEN
        v_record_id  := (OLD.id)::UUID;
        v_old_values := to_jsonb(OLD);
        v_new_values := to_jsonb(NEW);
    ELSIF TG_OP = 'DELETE' THEN
        v_record_id  := (OLD.id)::UUID;
        v_old_values := to_jsonb(OLD);
        v_new_values := NULL;
    END IF;

    INSERT INTO audit.audit_log (
        event_time, user_id, store_id, schema_name, table_name,
        operation, record_id, old_values, new_values, ip_address, correlation_id
    ) VALUES (
        now(), v_user_id, v_store_id, TG_TABLE_SCHEMA, TG_TABLE_NAME,
        TG_OP, v_record_id, v_old_values, v_new_values, v_ip, v_correlation_id
    );

    RETURN NULL; -- AFTER trigger; return value ignored
END;
$$;

-- ----------------------------------------------------------------------------
-- Attach audit triggers to critical tables
-- ----------------------------------------------------------------------------
CREATE OR REPLACE TRIGGER trg_audit_users
    AFTER INSERT OR UPDATE OR DELETE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION audit.fn_audit_trigger();

CREATE OR REPLACE TRIGGER trg_audit_user_roles
    AFTER INSERT OR UPDATE OR DELETE ON auth.user_roles
    FOR EACH ROW EXECUTE FUNCTION audit.fn_audit_trigger();

CREATE OR REPLACE TRIGGER trg_audit_stores
    AFTER INSERT OR UPDATE OR DELETE ON stores.stores
    FOR EACH ROW EXECUTE FUNCTION audit.fn_audit_trigger();

CREATE OR REPLACE TRIGGER trg_audit_soh_sessions
    AFTER INSERT OR UPDATE OR DELETE ON soh.soh_sessions
    FOR EACH ROW EXECUTE FUNCTION audit.fn_audit_trigger();

CREATE OR REPLACE TRIGGER trg_audit_soh_results
    AFTER INSERT OR DELETE ON soh.soh_results
    FOR EACH ROW EXECUTE FUNCTION audit.fn_audit_trigger();

CREATE OR REPLACE TRIGGER trg_audit_refill_tasks
    AFTER INSERT OR UPDATE OR DELETE ON refill.refill_tasks
    FOR EACH ROW EXECUTE FUNCTION audit.fn_audit_trigger();

CREATE OR REPLACE TRIGGER trg_audit_inventory_state
    AFTER UPDATE ON inventory.inventory_state
    FOR EACH ROW EXECUTE FUNCTION audit.fn_audit_trigger();
